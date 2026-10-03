package br.com.rony.ecommerce.adapters.domain.services.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.MercadoPagoGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static br.com.rony.ecommerce.adapters.domain.services.checkout.CheckoutCartService.error;

@Service
public class CheckoutWebhookService {
    private final CheckoutStore db;
    private final CheckoutCrypto crypto;
    private final MercadoPagoGateway gateway;
    private final Clock clock;
    private final byte[] secret;

    public CheckoutWebhookService(CheckoutStore db, CheckoutCrypto crypto, MercadoPagoGateway gateway,
            Clock clock, @Value("${checkout.gateway.webhook-secret}") String secret) {
        this.db = db; this.crypto = crypto; this.gateway = gateway; this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void receive(String resourceId, String requestId, String signature, String body) {
        if (resourceId == null || !resourceId.matches("[0-9]{1,30}")
                || requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,150}")
                || signature == null || signature.length() > 512 || body.length() > 65536)
            throw invalid();
        Map<String, String> parts = new HashMap<>();
        for (String part : signature.split(",")) {
            String[] entry = part.strip().split("=", 2);
            if (entry.length != 2 || parts.put(entry[0], entry[1]) != null) throw invalid();
        }
        String ts = parts.get("ts");
        String supplied = parts.get("v1");
        if (ts == null || !ts.matches("[0-9]{10,13}")
                || supplied == null || !supplied.matches("[a-fA-F0-9]{64}")) throw invalid();
        long timestamp = Long.parseLong(ts);
        Instant signedAt = ts.length() > 10 ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        if (Duration.between(signedAt, clock.instant()).abs().toSeconds() > 300) throw invalid();
        String manifest = "id:" + resourceId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String expected = CheckoutCrypto.hmacHex(secret, manifest);
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(supplied)))
            throw invalid();
        String eventKey = crypto.hash(manifest);
        boolean exists = db.tx(() -> db.optional("""
            SELECT id FROM webhook_logs WHERE gateway = 'MERCADO_PAGO' AND event_key = :event
            """, "event", eventKey).isPresent());
        if (exists) return;
        // O corpo não determina status nem external_reference: consultar o gateway autenticado.
        JsonNode payment = gateway.get(resourceId);
        String reference = payment.path("external_reference").asText();
        db.tx(() -> {
            var order = db.optional("SELECT id FROM orders WHERE public_id = :reference", "reference", reference);
            if (order.isEmpty()) return null;
            db.update("""
                INSERT INTO webhook_logs
                    (gateway, event_key, gateway_resource_id, payload_sha256, status, next_attempt_at, received_at)
                VALUES ('MERCADO_PAGO', :event, :resource, :hash, 'RECEIVED', :now, :now)
                ON DUPLICATE KEY UPDATE event_key = event_key
                """, "event", eventKey, "resource", resourceId, "hash", crypto.hash(body), "now", clock.instant());
            db.update("""
                INSERT INTO checkout_outbox
                    (event_key, aggregate_id, event_type, payload_ciphertext, status, next_attempt_at, created_at)
                VALUES (:event, :order, 'SYNC_PAYMENT', :payload, 'PENDING', :now, :now)
                ON DUPLICATE KEY UPDATE event_key = event_key
                """, "event", eventKey, "order", order.get().number("id"),
                "payload", crypto.encrypt("sync:" + eventKey, resourceId), "now", clock.instant());
            return null;
        });
    }

    private RuntimeException invalid() {
        return error(401, "WEBHOOK_INVALIDO", "Assinatura de webhook inválida.");
    }
}
