package br.com.rony.ecommerce.adapters.domain.services.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore.Row;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class PaymentResultService {
    private final CheckoutStore db;
    private final CheckoutCrypto crypto;
    private final CheckoutGuard audit;
    private final Clock clock;
    private final String collectorId;

    public PaymentResultService(CheckoutStore db, CheckoutCrypto crypto,
            CheckoutGuard audit, Clock clock,
            @Value("${checkout.gateway.collector-id}") String collectorId) {
        this.db = db; this.crypto = crypto; this.audit = audit;
        this.clock = clock; this.collectorId = collectorId;
    }

    public void apply(long orderId, JsonNode payment) {
        db.tx(() -> {
            Row order = db.one("SELECT * FROM orders WHERE id = :id FOR UPDATE", "id", orderId);
            Row transaction = db.one("""
                SELECT * FROM payment_transactions WHERE order_id = :order
                ORDER BY id DESC LIMIT 1 FOR UPDATE
                """, "order", orderId);
            verify(order, transaction, payment);
            Instant updated = OffsetDateTime.parse(payment.path("date_last_updated").asText()).toInstant();
            Instant previous = transaction.instant("gateway_updated_at");
            if (previous != null && updated.isBefore(previous)) return null;
            String status = status(payment);
            String oldStatus = order.text("payment_status");
            if (!allowed(oldStatus, status))
                throw new IllegalStateException("Transição exige reconciliação.");
            String publicId = order.text("public_id");
            String paymentId = payment.path("id").asText();
            JsonNode safe = sanitized(payment);
            db.update("""
                UPDATE payment_transactions
                SET gateway_transaction_id = :gatewayId, status = :status,
                    gateway_updated_at = :gatewayUpdated, gateway_result_ciphertext = :result,
                    card_last_four = :lastFour, card_brand = :brand,
                    pix_expires_at = :pixExpires, updated_at = :now, version = version + 1
                WHERE id = :id
                """, "gatewayId", paymentId, "status", status, "gatewayUpdated", updated,
                "result", crypto.encrypt("payment-result:" + publicId, crypto.json(safe)),
                "lastFour", lastFour(payment), "brand", payment.path("payment_method_id").asText(null),
                "pixExpires", instantOrNull(payment.path("date_of_expiration")),
                "now", clock.instant(), "id", transaction.number("id"));
            db.update("""
                UPDATE orders SET payment_status = :status, updated_at = :now,
                    version = version + 1 WHERE id = :id
                """, "status", status, "now", clock.instant(), "id", orderId);
            String eventKey = crypto.hash(paymentId + "|" + status + "|" + updated
                + "|" + payment.path("transaction_amount_refunded").asText());
            db.update("""
                INSERT INTO payment_events
                    (order_id, transaction_id, event_key, event_type, previous_status,
                     new_status, source, occurred_at, created_at)
                VALUES (:order, :transaction, :key, 'GATEWAY_STATUS', :old,
                    :status, 'GATEWAY', :occurred, :now)
                ON DUPLICATE KEY UPDATE event_key = event_key
                """, "order", orderId, "transaction", transaction.number("id"), "key", eventKey,
                "old", oldStatus, "status", status, "occurred", updated, "now", clock.instant());
            boolean paid = Set.of("APPROVED", "PARTIALLY_REFUNDED", "REFUNDED", "CHARGEBACK").contains(status);
            if (paid) coupon(orderId, true);
            else if (Set.of("DECLINED", "CANCELLED", "EXPIRED").contains(status)) coupon(orderId, false);
            if (status.equals("APPROVED")) {
                db.update("""
                    INSERT INTO checkout_outbox
                        (event_key, aggregate_id, event_type, status, next_attempt_at, created_at)
                    VALUES (:key, :order, 'CONFIRMATION_EMAIL', 'PENDING', :now, :now)
                    ON DUPLICATE KEY UPDATE event_key = event_key
                    """, "key", "EMAIL_APPROVED:" + publicId, "order", orderId, "now", clock.instant());
            }
            audit.audit(order.number("customer_id"), orderId, "PAYMENT_STATUS", status, null);
            return null;
        });
    }

    public void failDefinitively(long orderId, String safeCode) {
        db.tx(() -> {
            Row order = db.one("SELECT * FROM orders WHERE id = :id FOR UPDATE", "id", orderId);
            if (!Set.of("PROCESSING", "CREATED").contains(order.text("payment_status"))) return null;
            db.update("""
                UPDATE payment_transactions SET status = 'DECLINED', last_error_code = :code,
                    updated_at = :now WHERE order_id = :order
                """, "code", safeCode, "now", clock.instant(), "order", orderId);
            db.update("UPDATE orders SET payment_status = 'DECLINED', updated_at = :now WHERE id = :order",
                "now", clock.instant(), "order", orderId);
            coupon(orderId, false);
            audit.audit(order.number("customer_id"), orderId, "PAYMENT_CREATE", "DECLINED", null);
            return null;
        });
    }

    private void coupon(long orderId, boolean consume) {
        Optional<Row> redemption = db.optional(
            "SELECT * FROM coupon_redemptions WHERE order_id = :order FOR UPDATE", "order", orderId);
        if (redemption.isEmpty() || !"RESERVED".equals(redemption.get().text("status"))) return;
        Row reservation = redemption.get();
        db.one("SELECT id FROM coupons WHERE id = :id FOR UPDATE", "id", reservation.number("coupon_id"));
        db.update("""
            UPDATE coupons SET reserved_uses = reserved_uses - 1,
                current_uses = current_uses + :consumed, version = version + 1
            WHERE id = :id AND reserved_uses > 0
            """, "consumed", consume ? 1 : 0, "id", reservation.number("coupon_id"));
        db.update("UPDATE coupon_redemptions SET status = :status, updated_at = :now WHERE id = :id",
            "status", consume ? "CONSUMED" : "RELEASED", "now", clock.instant(), "id", reservation.number("id"));
    }

    private void verify(Row order, Row transaction, JsonNode payment) {
        String expectedType = switch (order.text("payment_method")) {
            case "PIX" -> "bank_transfer";
            case "CREDIT_CARD" -> "credit_card";
            case "DEBIT_CARD" -> "debit_card";
            default -> "";
        };
        boolean correct = order.text("public_id").equals(payment.path("external_reference").asText())
            && collectorId.equals(payment.path("collector_id").asText())
            && "BRL".equals(payment.path("currency_id").asText())
            && expectedType.equals(payment.path("payment_type_id").asText())
            && order.money("total_amount").compareTo(payment.path("transaction_amount").decimalValue()) == 0;
        if ("PIX".equals(order.text("payment_method")))
            correct &= "pix".equals(payment.path("payment_method_id").asText());
        String storedId = transaction.text("gateway_transaction_id");
        correct &= storedId == null || storedId.equals(payment.path("id").asText());
        if (!correct) throw new IllegalStateException("Resposta de pagamento divergente.");
    }

    public static String status(JsonNode payment) {
        String status = payment.path("status").asText();
        BigDecimal refunded = payment.path("transaction_amount_refunded").decimalValue();
        return switch (status) {
            case "approved" -> refunded.signum() > 0 ? "PARTIALLY_REFUNDED" : "APPROVED";
            case "rejected" -> "DECLINED";
            case "cancelled" -> "CANCELLED";
            case "refunded" -> "REFUNDED";
            case "charged_back" -> "CHARGEBACK";
            case "pending", "in_process" -> payment.path("status_detail").asText().contains("challenge")
                ? "REQUIRES_ACTION" : "PENDING";
            default -> "RECONCILIATION_REQUIRED";
        };
    }

    public static boolean allowed(String old, String next) {
        if (old.equals(next)) return true;
        if (Set.of("CREATED", "PROCESSING", "PENDING", "REQUIRES_ACTION", "RECONCILIATION_REQUIRED").contains(old))
            return true;
        if (old.equals("APPROVED")) return Set.of("PARTIALLY_REFUNDED", "REFUNDED", "CHARGEBACK").contains(next);
        if (old.equals("PARTIALLY_REFUNDED")) return Set.of("REFUNDED", "CHARGEBACK").contains(next);
        return false;
    }

    private JsonNode sanitized(JsonNode payment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id_transacao_gateway", payment.path("id").asText());
        result.put("status", status(payment));
        result.put("parcelas", payment.path("installments").asInt(1));
        if (lastFour(payment) != null) {
            result.put("cartao_mascarado", "****" + lastFour(payment));
            result.put("bandeira", payment.path("payment_method_id").asText());
        }
        JsonNode data = payment.path("point_of_interaction").path("transaction_data");
        if ("pix".equals(payment.path("payment_method_id").asText())) {
            result.put("pix", Map.of("copia_cola", data.path("qr_code").asText(),
                "qr_code_base64", data.path("qr_code_base64").asText(),
                "data_expiracao", payment.path("date_of_expiration").asText()));
        }
        if ("REQUIRES_ACTION".equals(status(payment))) {
            JsonNode info = payment.path("three_ds_info");
            result.put("autenticacao_adicional", Map.of(
                "external_resource_url", info.path("external_resource_url").asText(),
                "creq", info.path("creq").asText()));
        }
        return crypto.tree(crypto.json(result));
    }

    private String lastFour(JsonNode payment) {
        String value = payment.path("card").path("last_four_digits").asText();
        return value.matches("[0-9]{4}") ? value : null;
    }

    private Instant instantOrNull(JsonNode value) {
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) return null;
        return OffsetDateTime.parse(value.asText()).toInstant();
    }
}
