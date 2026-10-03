CREATE TABLE checkout_customers (
 id BIGINT NOT NULL AUTO_INCREMENT, subject_key CHAR(64) NOT NULL,
 personal_data_ciphertext MEDIUMTEXT NULL, trusted_customer BOOLEAN NOT NULL DEFAULT FALSE,
 legal_hold BOOLEAN NOT NULL DEFAULT FALSE, retain_until DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (id), CONSTRAINT uk_checkout_customer_subject UNIQUE (subject_key),
 INDEX idx_checkout_customer_retention (retain_until)
);
CREATE TABLE checkout_carts (
 id BIGINT NOT NULL AUTO_INCREMENT, customer_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN', coupon_code VARCHAR(40) NULL,
 revision BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT fk_checkout_cart_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 INDEX idx_checkout_cart_customer_status (customer_id, status)
);
CREATE TABLE checkout_cart_items (
 id BIGINT NOT NULL AUTO_INCREMENT, cart_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 quantity INT NOT NULL, PRIMARY KEY (id), CONSTRAINT uk_checkout_cart_product UNIQUE (cart_id, product_id),
 CONSTRAINT fk_checkout_cart_item_cart FOREIGN KEY (cart_id) REFERENCES checkout_carts(id),
 CONSTRAINT fk_checkout_cart_item_product FOREIGN KEY (product_id) REFERENCES products(id),
 CONSTRAINT ck_checkout_cart_quantity CHECK (quantity > 0)
);
CREATE TABLE product_shipping_profiles (
 product_id BIGINT NOT NULL, weight_kg DECIMAL(12,3) NOT NULL,
 height_cm DECIMAL(12,2) NOT NULL, width_cm DECIMAL(12,2) NOT NULL, length_cm DECIMAL(12,2) NOT NULL,
 updated_at DATETIME(6) NOT NULL, PRIMARY KEY (product_id),
 CONSTRAINT fk_shipping_profile_product FOREIGN KEY (product_id) REFERENCES products(id),
 CONSTRAINT ck_shipping_profile_measurements CHECK (weight_kg > 0 AND height_cm > 0 AND width_cm > 0 AND length_cm > 0)
);
CREATE TABLE coupons (
 id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 discount_type VARCHAR(20) NOT NULL, discount_value DECIMAL(15,2) NOT NULL,
 minimum_purchase DECIMAL(15,2) NOT NULL DEFAULT 0, maximum_discount DECIMAL(15,2) NULL,
 starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL,
 maximum_uses BIGINT NULL, maximum_uses_per_customer BIGINT NULL,
 current_uses BIGINT NOT NULL DEFAULT 0, reserved_uses BIGINT NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (id), CONSTRAINT uk_checkout_coupon_code UNIQUE (code),
 CONSTRAINT ck_coupon_type CHECK (discount_type IN ('PERCENTAGE','FIXED')),
 CONSTRAINT ck_coupon_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
 CONSTRAINT ck_coupon_period CHECK (ends_at > starts_at),
 CONSTRAINT ck_coupon_limits CHECK (
  minimum_purchase >= 0 AND (maximum_discount IS NULL OR maximum_discount > 0)
  AND (maximum_uses IS NULL OR maximum_uses > 0)
  AND (maximum_uses_per_customer IS NULL OR maximum_uses_per_customer > 0)
  AND current_uses >= 0 AND reserved_uses >= 0
  AND (maximum_uses IS NULL OR current_uses + reserved_uses <= maximum_uses)
 ), INDEX idx_coupon_validity (active, starts_at, ends_at)
);
CREATE TABLE coupon_products (
 coupon_id BIGINT NOT NULL, product_id BIGINT NOT NULL, PRIMARY KEY (coupon_id, product_id),
 CONSTRAINT fk_coupon_product_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
 CONSTRAINT fk_coupon_product_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE coupon_categories (
 coupon_id BIGINT NOT NULL, category_id BIGINT NOT NULL, PRIMARY KEY (coupon_id, category_id),
 CONSTRAINT fk_coupon_category_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
 CONSTRAINT fk_coupon_category_category FOREIGN KEY (category_id) REFERENCES categories(id)
);
CREATE TABLE checkout_postal_codes (
 postal_code CHAR(8) NOT NULL, city VARCHAR(120) NOT NULL, state CHAR(2) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, updated_at DATETIME(6) NOT NULL, PRIMARY KEY (postal_code)
);
CREATE TABLE checkout_shipping_tariffs (
 id BIGINT NOT NULL AUTO_INCREMENT, service_code VARCHAR(40) NOT NULL, service_name VARCHAR(100) NOT NULL,
 postal_code_start CHAR(8) NOT NULL, postal_code_end CHAR(8) NOT NULL, delivery_business_days INT NOT NULL,
 base_price DECIMAL(15,2) NOT NULL, price_per_kg DECIMAL(15,2) NOT NULL, volumetric_divisor DECIMAL(12,2) NOT NULL,
 maximum_weight_kg DECIMAL(12,3) NOT NULL, maximum_volume_cm3 DECIMAL(18,2) NOT NULL,
 maximum_item_height_cm DECIMAL(12,2) NOT NULL, maximum_item_width_cm DECIMAL(12,2) NOT NULL,
 maximum_item_length_cm DECIMAL(12,2) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT ck_shipping_tariff_values CHECK (
  postal_code_start <= postal_code_end AND delivery_business_days >= 0 AND base_price >= 0 AND price_per_kg >= 0
  AND volumetric_divisor > 0 AND maximum_weight_kg > 0 AND maximum_volume_cm3 > 0
  AND maximum_item_height_cm > 0 AND maximum_item_width_cm > 0 AND maximum_item_length_cm > 0
 ), INDEX idx_shipping_tariff_region (active, postal_code_start, postal_code_end)
);
CREATE TABLE checkout_shipping_quotes (
 id CHAR(36) NOT NULL, cart_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, tariff_id BIGINT NOT NULL,
 cart_fingerprint CHAR(64) NOT NULL, postal_code CHAR(8) NOT NULL,
 service_code VARCHAR(40) NOT NULL, service_name VARCHAR(100) NOT NULL, price DECIMAL(15,2) NOT NULL,
 delivery_business_days INT NOT NULL, estimated_delivery_date DATE NOT NULL,
 expires_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, PRIMARY KEY (id),
 CONSTRAINT fk_shipping_quote_cart FOREIGN KEY (cart_id) REFERENCES checkout_carts(id),
 CONSTRAINT fk_shipping_quote_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 CONSTRAINT fk_shipping_quote_tariff FOREIGN KEY (tariff_id) REFERENCES checkout_shipping_tariffs(id),
 INDEX idx_shipping_quote_cache (cart_id, postal_code, cart_fingerprint, expires_at),
 INDEX idx_shipping_quote_expiration (expires_at)
);
CREATE TABLE checkout_shipping_selections (
 cart_id BIGINT NOT NULL, quote_id CHAR(36) NOT NULL, selected_at DATETIME(6) NOT NULL, PRIMARY KEY (cart_id),
 CONSTRAINT fk_shipping_selection_cart FOREIGN KEY (cart_id) REFERENCES checkout_carts(id),
 CONSTRAINT fk_shipping_selection_quote FOREIGN KEY (quote_id) REFERENCES checkout_shipping_quotes(id)
);
CREATE TABLE orders (
 id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL,
 customer_id BIGINT NOT NULL, cart_id BIGINT NOT NULL, payment_status VARCHAR(30) NOT NULL,
 payment_method VARCHAR(30) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'BRL',
 subtotal DECIMAL(15,2) NOT NULL, automatic_discount DECIMAL(15,2) NOT NULL,
 coupon_discount DECIMAL(15,2) NOT NULL, shipping_amount DECIMAL(15,2) NOT NULL, total_amount DECIMAL(15,2) NOT NULL,
 coupon_id BIGINT NULL, coupon_code VARCHAR(40) NULL, shipping_service VARCHAR(100) NOT NULL,
 delivery_business_days INT NOT NULL, customer_data_ciphertext MEDIUMTEXT NULL,
 delivery_address_ciphertext MEDIUMTEXT NULL, pii_retain_until DATETIME(6) NOT NULL,
 legal_hold BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT uk_checkout_order_public_id UNIQUE (public_id), CONSTRAINT uk_checkout_order_cart UNIQUE (cart_id),
 CONSTRAINT fk_checkout_order_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 CONSTRAINT fk_checkout_order_cart FOREIGN KEY (cart_id) REFERENCES checkout_carts(id),
 CONSTRAINT fk_checkout_order_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
 CONSTRAINT ck_checkout_order_amounts CHECK (
  subtotal >= 0 AND automatic_discount >= 0 AND coupon_discount >= 0 AND shipping_amount >= 0 AND total_amount >= 0
  AND automatic_discount + coupon_discount <= subtotal
  AND total_amount = subtotal - automatic_discount - coupon_discount + shipping_amount
 ), INDEX idx_checkout_order_customer_date (customer_id, created_at),
 INDEX idx_checkout_order_payment_status (payment_status, updated_at),
 INDEX idx_checkout_order_retention (pii_retain_until, legal_hold)
);
CREATE TABLE order_items (
 id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL, product_id BIGINT NULL,
 product_name VARCHAR(150) NOT NULL, product_photo VARCHAR(1024) NULL,
 unit_price DECIMAL(15,2) NOT NULL, quantity INT NOT NULL, subtotal DECIMAL(15,2) NOT NULL,
 automatic_discount DECIMAL(15,2) NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT fk_checkout_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
 CONSTRAINT fk_checkout_order_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
 CONSTRAINT ck_checkout_order_item_values CHECK (
  quantity > 0 AND unit_price >= 0 AND subtotal = unit_price * quantity
  AND automatic_discount >= 0 AND automatic_discount <= subtotal
 ), INDEX idx_checkout_order_item_order (order_id)
);
CREATE TABLE coupon_redemptions (
 id BIGINT NOT NULL AUTO_INCREMENT, coupon_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, reserved_until DATETIME(6) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id), CONSTRAINT uk_coupon_redemption_order UNIQUE (order_id),
 CONSTRAINT fk_coupon_redemption_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
 CONSTRAINT fk_coupon_redemption_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 CONSTRAINT fk_coupon_redemption_order FOREIGN KEY (order_id) REFERENCES orders(id),
 CONSTRAINT ck_coupon_redemption_status CHECK (status IN ('RESERVED','CONSUMED','RELEASED')),
 INDEX idx_coupon_customer_usage (coupon_id, customer_id, status),
 INDEX idx_coupon_reservation_expiration (status, reserved_until)
);
CREATE TABLE payment_transactions (
 id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL, operation_key CHAR(36) NOT NULL,
 gateway VARCHAR(40) NOT NULL, gateway_transaction_id VARCHAR(120) NULL, status VARCHAR(30) NOT NULL,
 amount DECIMAL(15,2) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'BRL', method VARCHAR(30) NOT NULL,
 installments INT NOT NULL DEFAULT 1, card_last_four CHAR(4) NULL, card_brand VARCHAR(40) NULL,
 pix_expires_at DATETIME(6) NULL, gateway_result_ciphertext MEDIUMTEXT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (id), CONSTRAINT uk_payment_operation_key UNIQUE (operation_key),
 CONSTRAINT uk_gateway_transaction UNIQUE (gateway, gateway_transaction_id),
 CONSTRAINT fk_payment_transaction_order FOREIGN KEY (order_id) REFERENCES orders(id),
 CONSTRAINT ck_payment_transaction_values CHECK (amount >= 0 AND installments BETWEEN 1 AND 12),
 INDEX idx_payment_order (order_id, created_at), INDEX idx_payment_status (status, updated_at)
);
CREATE TABLE payment_events (
 id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL, transaction_id BIGINT NULL,
 event_key VARCHAR(160) NOT NULL, event_type VARCHAR(60) NOT NULL,
 previous_status VARCHAR(30) NULL, new_status VARCHAR(30) NOT NULL, source VARCHAR(30) NOT NULL,
 occurred_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, PRIMARY KEY (id),
 CONSTRAINT uk_payment_event_key UNIQUE (event_key),
 CONSTRAINT fk_payment_event_order FOREIGN KEY (order_id) REFERENCES orders(id),
 CONSTRAINT fk_payment_event_transaction FOREIGN KEY (transaction_id) REFERENCES payment_transactions(id),
 INDEX idx_payment_event_order (order_id, occurred_at)
);
-- Não armazenar corpo arbitrário do gateway: somente metadados permitidos.
CREATE TABLE webhook_logs (
 id BIGINT NOT NULL AUTO_INCREMENT, gateway VARCHAR(40) NOT NULL, event_key VARCHAR(160) NOT NULL,
 gateway_resource_id VARCHAR(120) NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED', attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL, locked_until DATETIME(6) NULL, lock_token CHAR(36) NULL,
 last_error_code VARCHAR(80) NULL, received_at DATETIME(6) NOT NULL, processed_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT uk_webhook_gateway_event UNIQUE (gateway, event_key),
 INDEX idx_webhook_processing (status, next_attempt_at, locked_until)
);
CREATE TABLE checkout_idempotency_keys (
 id BIGINT NOT NULL AUTO_INCREMENT, customer_id BIGINT NOT NULL, operation VARCHAR(60) NOT NULL,
 key_sha256 CHAR(64) NOT NULL, request_hmac CHAR(64) NOT NULL, order_id BIGINT NULL,
 status VARCHAR(20) NOT NULL, response_http_status INT NULL, response_ciphertext MEDIUMTEXT NULL,
 expires_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT uk_checkout_idempotency UNIQUE (customer_id, operation, key_sha256),
 CONSTRAINT fk_checkout_idempotency_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 CONSTRAINT fk_checkout_idempotency_order FOREIGN KEY (order_id) REFERENCES orders(id),
 INDEX idx_checkout_idempotency_expiration (status, expires_at)
);
-- Outbox: nunca incluir PAN, CVV ou PIN.
CREATE TABLE checkout_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT, event_key VARCHAR(160) NOT NULL, aggregate_id BIGINT NOT NULL,
 event_type VARCHAR(60) NOT NULL, payload_ciphertext MEDIUMTEXT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL, locked_until DATETIME(6) NULL, lock_token CHAR(36) NULL,
 last_error_code VARCHAR(80) NULL, created_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
 CONSTRAINT uk_checkout_outbox_event UNIQUE (event_key),
 INDEX idx_checkout_outbox_processing (status, next_attempt_at, locked_until)
);
CREATE TABLE checkout_consents (
 id BIGINT NOT NULL AUTO_INCREMENT, customer_id BIGINT NOT NULL, purpose VARCHAR(100) NOT NULL,
 policy_version VARCHAR(40) NOT NULL, granted BOOLEAN NOT NULL, recorded_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id), CONSTRAINT fk_checkout_consent_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 INDEX idx_checkout_consent_history (customer_id, purpose, recorded_at)
);
CREATE TABLE checkout_privacy_requests (
 id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, customer_id BIGINT NOT NULL,
 request_type VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, reason_code VARCHAR(80) NULL,
 created_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL, PRIMARY KEY (id),
 CONSTRAINT uk_checkout_privacy_request UNIQUE (public_id),
 CONSTRAINT fk_checkout_privacy_customer FOREIGN KEY (customer_id) REFERENCES checkout_customers(id),
 INDEX idx_checkout_privacy_processing (status, created_at)
);
CREATE TABLE checkout_audit_logs (
 id BIGINT NOT NULL AUTO_INCREMENT, customer_id BIGINT NULL, order_id BIGINT NULL,
 action VARCHAR(80) NOT NULL, outcome VARCHAR(30) NOT NULL, reason_code VARCHAR(80) NULL,
 correlation_id CHAR(36) NOT NULL, ip_hmac CHAR(64) NULL,
 retain_until DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, PRIMARY KEY (id),
 INDEX idx_checkout_audit_customer (customer_id, created_at),
 INDEX idx_checkout_audit_order (order_id, created_at), INDEX idx_checkout_audit_ip (ip_hmac, created_at),
 INDEX idx_checkout_audit_retention (retain_until)
);
