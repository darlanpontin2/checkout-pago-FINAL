package br.com.rony.ecommerce.adapters.domain.services.checkout;

import br.com.rony.ecommerce.adapters.data.repository.checkout.CheckoutStore;
import br.com.rony.ecommerce.adapters.infrastructure.checkout.CheckoutCrypto;
import br.com.rony.ecommerce.application.dto.checkout.CheckoutForms.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class CheckoutPrivacyService {
    private final CheckoutStore db;
    private final CheckoutCrypto crypto;
    private final CheckoutGuard audit;
    private final CustomerValidationServiceImpl validator;
    private final Clock clock;

    public CheckoutPrivacyService(CheckoutStore db, CheckoutCrypto crypto, CheckoutGuard audit,
            CustomerValidationServiceImpl validator, Clock clock) {
        this.db = db; this.crypto = crypto; this.audit = audit;
        this.validator = validator; this.clock = clock;
    }

    public void consent(long customer, Consent form) {
        db.tx(() -> {
            db.update("""
                INSERT INTO checkout_consents (customer_id, purpose, policy_version, granted, recorded_at)
                VALUES (:customer, :purpose, :version, :granted, :now)
                """, "customer", customer, "purpose", form.finalidade(), "version", form.versao_politica(),
                "granted", form.concedido(), "now", clock.instant());
            audit.audit(customer, null, "CONSENT", "RECORDED", null);
            return null;
        });
    }

    public void rectify(long customer, Rectification form) {
        validator.validate(form.dados_cliente(), form.endereco());
        db.tx(() -> {
            db.update("""
                UPDATE checkout_customers SET personal_data_ciphertext = :data,
                    updated_at = :now, version = version + 1 WHERE id = :id
                """, "data", crypto.encrypt("customer:" + customer, crypto.json(form)),
                "now", clock.instant(), "id", customer);
            audit.audit(customer, null, "PERSONAL_DATA_RECTIFY", "SUCCESS", null);
            return null;
        });
    }

    public Map<String, Object> export(long customer) {
        return db.tx(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            var profile = db.one("SELECT personal_data_ciphertext FROM checkout_customers WHERE id = :id", "id", customer);
            result.put("cadastro", profile.text("personal_data_ciphertext") == null ? null : crypto.tree(
                crypto.decrypt("customer:" + customer, profile.text("personal_data_ciphertext"))));
            result.put("consentimentos", db.rows("""
                SELECT purpose, policy_version, granted, recorded_at FROM checkout_consents
                WHERE customer_id = :id ORDER BY id
                """, "id", customer).stream().map(row -> row.values()).toList());
            List<Map<String, Object>> orders = new ArrayList<>();
            for (var order : db.rows("SELECT * FROM orders WHERE customer_id = :id ORDER BY id", "id", customer)) {
                Map<String, Object> value = new LinkedHashMap<>();
                String publicId = order.text("public_id");
                value.put("id_pedido", publicId);
                value.put("status", order.text("payment_status"));
                value.put("total", order.money("total_amount"));
                if (order.text("customer_data_ciphertext") != null)
                    value.put("dados_cliente", crypto.tree(crypto.decrypt("order-customer:" + publicId,
                        order.text("customer_data_ciphertext"))));
                if (order.text("delivery_address_ciphertext") != null)
                    value.put("endereco", crypto.tree(crypto.decrypt("order-address:" + publicId,
                        order.text("delivery_address_ciphertext"))));
                value.put("itens", db.rows("""
                    SELECT product_name, unit_price, quantity, subtotal FROM order_items WHERE order_id = :id
                    """, "id", order.number("id")).stream().map(row -> row.values()).toList());
                orders.add(value);
            }
            result.put("pedidos", orders);
            audit.audit(customer, null, "PERSONAL_DATA_EXPORT", "SUCCESS", null);
            return result;
        });
    }

    public String requestErasure(long customer) {
        return db.tx(() -> {
            String id = UUID.randomUUID().toString();
            db.update("""
                INSERT INTO checkout_privacy_requests (public_id, customer_id, request_type, status, created_at)
                VALUES (:id, :customer, 'ERASURE', 'PENDING', :now)
                """, "id", id, "customer", customer, "now", clock.instant());
            audit.audit(customer, null, "ERASURE_REQUEST", "RECORDED", null);
            return id;
        });
    }

    @Scheduled(fixedDelayString = "${checkout.privacy.cleanup-ms:3600000}")
    public void eraseExpiredData() {
        db.tx(() -> {
            Instant now = clock.instant();
            // Esta rotina não equivale à política completa de retenção de todas as cópias.
            db.update("""
                UPDATE orders SET customer_data_ciphertext = NULL, delivery_address_ciphertext = NULL
                WHERE legal_hold = FALSE AND pii_retain_until < :now
                  AND payment_status IN ('APPROVED', 'DECLINED', 'CANCELLED', 'EXPIRED',
                      'PARTIALLY_REFUNDED', 'REFUNDED', 'CHARGEBACK')
                  AND NOT EXISTS (SELECT 1 FROM checkout_outbox x
                      WHERE x.aggregate_id = orders.id AND x.status <> 'COMPLETED')
                """, "now", now);
            for (var request : db.rows("""
                SELECT * FROM checkout_privacy_requests WHERE request_type = 'ERASURE'
                  AND status IN ('PENDING', 'WAITING_RETENTION')
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                """)) {
                long customer = request.number("customer_id");
                var profile = db.one("SELECT * FROM checkout_customers WHERE id = :id FOR UPDATE", "id", customer);
                boolean retained = profile.bool("legal_hold") || db.one("""
                    SELECT COUNT(*) AS total FROM orders WHERE customer_id = :id
                      AND (legal_hold = TRUE OR pii_retain_until >= :now
                           OR customer_data_ciphertext IS NOT NULL OR delivery_address_ciphertext IS NOT NULL)
                    """, "id", customer, "now", now).number("total") > 0;
                if (!retained) {
                    db.update("UPDATE checkout_customers SET personal_data_ciphertext = NULL, updated_at = :now WHERE id = :id",
                        "now", now, "id", customer);
                }
                db.update("""
                    UPDATE checkout_privacy_requests SET status = :status, reason_code = :reason,
                        completed_at = :completed WHERE id = :id
                    """, "status", retained ? "WAITING_RETENTION" : "COMPLETED",
                    "reason", retained ? "RETENTION_REQUIRED" : null,
                    "completed", retained ? null : now, "id", request.number("id"));
                audit.audit(customer, null, "ERASURE", retained ? "DEFERRED" : "COMPLETED", null);
            }
            return null;
        });
    }
}
