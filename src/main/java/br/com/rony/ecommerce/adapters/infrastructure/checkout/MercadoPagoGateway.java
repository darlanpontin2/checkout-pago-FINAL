package br.com.rony.ecommerce.adapters.infrastructure.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class MercadoPagoGateway {
    private static final String BASE = "https://api.mercadopago.com";
    private final HttpClient client;
    private final CheckoutCrypto crypto;
    private final String accessToken;

    public MercadoPagoGateway(CheckoutCrypto crypto,
            @Value("${checkout.gateway.access-token}") String accessToken) {
        this.crypto = crypto;
        this.accessToken = accessToken;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public JsonNode create(String operationKey, JsonNode command, String expectedMethod) {
        if (!expectedMethod.equals("PIX")) {
            String methodId = command.path("payment_method_id").asText();
            String expectedType = expectedMethod.equals("CREDIT_CARD") ? "credit_card" : "debit_card";
            JsonNode methods = request("GET", "/v1/payment_methods", null, null);
            boolean available = false;
            for (JsonNode method : methods) {
                if (methodId.equals(method.path("id").asText())
                        && expectedType.equals(method.path("payment_type_id").asText())
                        && "active".equals(method.path("status").asText())) {
                    available = true;
                }
            }
            if (!available) throw new GatewayFailure(false, "PAYMENT_METHOD_UNAVAILABLE");
        }
        return request("POST", "/v1/payments", operationKey, crypto.json(command));
    }

    public JsonNode get(String paymentId) {
        if (paymentId == null || !paymentId.matches("[0-9]{1,30}"))
            throw new GatewayFailure(false, "INVALID_GATEWAY_ID");
        return request("GET", "/v1/payments/" + paymentId, null, null);
    }

    public Optional<JsonNode> findByReference(String publicId) {
        UUID.fromString(publicId);
        JsonNode result = request("GET", "/v1/payments/search?external_reference=" + publicId, null, null);
        JsonNode results = result.path("results");
        if (results.size() > 1) throw new GatewayFailure(true, "MULTIPLE_GATEWAY_PAYMENTS");
        if (results.isEmpty()) return Optional.empty();
        return Optional.of(get(results.get(0).path("id").asText()));
    }

    private JsonNode request(String method, String path, String operationKey, String body) {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");
            if (body == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                    .header("X-Idempotency-Key", operationKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            }
            try {
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return crypto.tree(response.body());
                if (status == 429 || status >= 500) {
                    if (attempt < 2) { pause(attempt); continue; }
                    throw new GatewayFailure(true, "GATEWAY_TEMPORARY_FAILURE");
                }
                if (status == 401 || status == 403)
                    throw new GatewayFailure(true, "GATEWAY_CONFIGURATION");
                // Não registrar body ou mensagens brutas do provedor.
                throw new GatewayFailure(false, "GATEWAY_REQUEST_REJECTED");
            } catch (IOException exception) {
                if (attempt < 2) { pause(attempt); continue; }
                throw new GatewayFailure(true, "GATEWAY_NETWORK_UNKNOWN");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new GatewayFailure(true, "GATEWAY_INTERRUPTED");
            }
        }
        throw new GatewayFailure(true, "GATEWAY_UNKNOWN");
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(250L * (1L << attempt) + new Random().nextInt(100));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GatewayFailure(true, "GATEWAY_INTERRUPTED");
        }
    }

    public static class GatewayFailure extends RuntimeException {
        private final boolean uncertain;
        private final String code;
        public GatewayFailure(boolean uncertain, String code) {
            super(code); this.uncertain = uncertain; this.code = code;
        }
        public boolean uncertain() { return uncertain; }
        public String code() { return code; }
    }
}
