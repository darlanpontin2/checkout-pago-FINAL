package br.com.rony.ecommerce.adapters.infrastructure.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore.Row;
import br.com.rony.ecommerce.adapters.domain.services.checkout.PaymentResultService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class CheckoutWorkers {
    private final CheckoutStore db;
    private final CheckoutCrypto crypto;
    private final MercadoPagoGateway gateway;
    private final PaymentResultService results;
    private final JavaMailSender mail;
    private final Clock clock;
    private final String sender;

    public CheckoutWorkers(CheckoutStore db, CheckoutCrypto crypto, MercadoPagoGateway gateway,
            PaymentResultService results, JavaMailSender mail, Clock clock,
            @Value("${checkout.email.from}") String sender) {
        this.db = db; this.crypto = crypto; this.gateway = gateway;
        this.results = results; this.mail = mail; this.clock = clock; this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${checkout.worker.delay-ms:2000}")
    public void work() {
        for (int i = 0; i < 10; i++) {
            Optional<Row> claimed = claim();
            if (claimed.isEmpty()) return;
            Row job = claimed.get();
            try {
                process(job);
                finish(job, true, null);
            } catch (MercadoPagoGateway.GatewayFailure failure) {
                if (!failure.uncertain() && "CREATE_PAYMENT".equals(job.text("event_type"))
                        && job.instant("dispatched_at") == null) {
                    results.failDefinitively(job.number("aggregate_id"), failure.code());
                    finish(job, true, failure.code());
                } else {
                    finish(job, false, failure.code());
                }
            } catch (Exception exception) {
                // Não registrar mensagens externas potencialmente sensíveis.
                finish(job, false, "PROCESSING_FAILED");
            }
        }
    }

    private Optional<Row> claim() {
        return db.tx(() -> {
            Optional<Row> candidate = db.optional("""
                SELECT * FROM checkout_outbox
                WHERE status IN ('PENDING', 'RETRY', 'PROCESSING')
                  AND next_attempt_at <= :now
                  AND (locked_until IS NULL OR locked_until <= :now)
                ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, "now", clock.instant());
            if (candidate.isEmpty()) return Optional.empty();
            Row original = candidate.get();
            String token = UUID.randomUUID().toString();
            db.update("""
                UPDATE checkout_outbox SET status = 'PROCESSING', attempts = attempts + 1,
                    lock_token = :token, locked_until = :until WHERE id = :id
                """, "token", token, "until", clock.instant().plusSeconds(180), "id", original.number("id"));
            Map<String, Object> values = new LinkedHashMap<>(original.values());
            values.put("lock_token", token);
            values.put("attempts", original.number("attempts") + 1);
            return Optional.of(new Row(values));
        });
    }

    private void process(Row job) {
        long orderId = job.number("aggregate_id");
        Row order = db.tx(() -> db.one("SELECT * FROM orders WHERE id = :id", "id", orderId));
        String publicId = order.text("public_id");
        if ("CONFIRMATION_EMAIL".equals(job.text("event_type"))) {
            if (order.text("customer_data_ciphertext") == null)
                throw new IllegalStateException("Destinatário indisponível.");
            JsonNode customer = crypto.tree(crypto.decrypt("order-customer:" + publicId,
                order.text("customer_data_ciphertext")));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(customer.path("email").asText());
            message.setSubject("Pagamento confirmado — pedido " + publicId);
            message.setText("Recebemos a confirmação do pagamento do pedido " + publicId + ".");
            mail.send(message);
            return;
        }
        Row payment = db.tx(() -> db.one("""
            SELECT * FROM payment_transactions WHERE order_id = :order ORDER BY id DESC LIMIT 1
            """, "order", orderId));
        JsonNode response;
        if ("SYNC_PAYMENT".equals(job.text("event_type"))) {
            String resourceId = crypto.decrypt("sync:" + job.text("event_key"), job.text("payload_ciphertext"));
            response = gateway.get(resourceId);
        } else if (payment.text("gateway_transaction_id") != null) {
            response = gateway.get(payment.text("gateway_transaction_id"));
        } else if (job.instant("dispatched_at") != null) {
            response = gateway.findByReference(publicId).orElseThrow(() ->
                new MercadoPagoGateway.GatewayFailure(true, "PAYMENT_OUTCOME_UNKNOWN"));
        } else {
            db.tx(() -> {
                int changed = db.update("""
                    UPDATE checkout_outbox SET dispatched_at = :now
                    WHERE id = :id AND lock_token = :token AND status = 'PROCESSING'
                      AND locked_until > :now
                    """, "now", clock.instant(), "id", job.number("id"), "token", job.text("lock_token"));
                if (changed != 1) throw new IllegalStateException("Lease perdido.");
                return null;
            });
            JsonNode command = crypto.tree(crypto.decrypt("payment-command:" + publicId,
                job.text("payload_ciphertext")));
            response = gateway.create(payment.text("operation_key"), command, payment.text("method"));
        }
        results.apply(orderId, response);
    }

    private void finish(Row job, boolean success, String safeCode) {
        db.tx(() -> {
            int attempts = Math.toIntExact(job.number("attempts"));
            String status = success ? "COMPLETED" : attempts >= 8 ? "DEADLETTER" : "RETRY";
            long delay = Math.min(3600, 5L << Math.min(attempts, 9));
            db.update("""
                UPDATE checkout_outbox SET status = :status, last_error_code = :error,
                    next_attempt_at = :next, locked_until = NULL, lock_token = NULL,
                    completed_at = :completed,
                    payload_ciphertext = CASE WHEN :success THEN NULL ELSE payload_ciphertext END
                WHERE id = :id AND lock_token = :token
                """, "status", status, "error", safeCode, "next", clock.instant().plusSeconds(delay),
                "completed", success ? clock.instant() : null, "success", success,
                "id", job.number("id"), "token", job.text("lock_token"));
            if ("SYNC_PAYMENT".equals(job.text("event_type"))) {
                db.update("""
                    UPDATE webhook_logs SET status = :status, attempts = :attempts,
                        last_error_code = :error, processed_at = :processed WHERE event_key = :event
                    """, "status", success ? "PROCESSED" : status.equals("DEADLETTER") ? "DEADLETTER" : "RETRY",
                    "attempts", attempts, "error", safeCode, "processed", success ? clock.instant() : null,
                    "event", job.text("event_key"));
            }
            return null;
        });
    }

    @Scheduled(fixedDelayString = "${checkout.worker.reconcile-ms:60000}")
    public void reconcile() {
        List<Row> pending = db.tx(() -> db.rows("""
            SELECT order_id, gateway_transaction_id FROM payment_transactions
            WHERE status IN ('PENDING', 'PROCESSING', 'REQUIRES_ACTION', 'RECONCILIATION_REQUIRED')
              AND gateway_transaction_id IS NOT NULL ORDER BY updated_at LIMIT 100
            """));
        for (Row payment : pending) {
            try {
                results.apply(payment.number("order_id"), gateway.get(payment.text("gateway_transaction_id")));
            } catch (Exception ignored) {
                // Mantém pendente para reconciliação posterior.
            }
        }
    }

    @Scheduled(fixedDelayString = "${checkout.worker.cleanup-ms:3600000}")
    public void cleanup() {
        db.tx(() -> {
            Instant now = clock.instant();
            db.update("DELETE FROM checkout_rate_buckets WHERE expires_at < :now", "now", now);
            db.update("""
                DELETE s FROM checkout_shipping_selections s
                JOIN checkout_shipping_quotes q ON q.id = s.quote_id WHERE q.expires_at < :now
                """, "now", now);
            db.update("DELETE FROM checkout_shipping_quotes WHERE expires_at < :now", "now", now);
            // Não apagar chaves enquanto o resultado de pagamento é desconhecido.
            db.update("""
                DELETE k FROM checkout_idempotency_keys k JOIN orders o ON o.id = k.order_id
                WHERE k.expires_at < :now AND o.payment_status IN
                    ('APPROVED', 'DECLINED', 'CANCELLED', 'EXPIRED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'CHARGEBACK')
                """, "now", now);
            db.update("DELETE FROM checkout_audit_logs WHERE retain_until < :now", "now", now);
            return null;
        });
    }
}
