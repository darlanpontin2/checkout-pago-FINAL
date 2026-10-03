ALTER TABLE checkout_carts
 ADD COLUMN open_customer_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN customer_id ELSE NULL END) STORED,
 ADD CONSTRAINT uk_checkout_open_cart UNIQUE (open_customer_id);
CREATE TABLE checkout_promotions (
 id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NULL, category_id BIGINT NULL,
 percentage DECIMAL(5,2) NOT NULL, starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, PRIMARY KEY (id),
 FOREIGN KEY (product_id) REFERENCES products(id), FOREIGN KEY (category_id) REFERENCES categories(id),
 CHECK (percentage > 0 AND percentage <= 100), CHECK (ends_at > starts_at),
 INDEX idx_checkout_promotion_period (active, starts_at, ends_at)
);
ALTER TABLE payment_transactions
 ADD COLUMN gateway_updated_at DATETIME(6) NULL,
 ADD COLUMN last_error_code VARCHAR(80) NULL;
ALTER TABLE checkout_outbox ADD COLUMN dispatched_at DATETIME(6) NULL;
CREATE TABLE checkout_rate_buckets (
 bucket_key CHAR(64) NOT NULL, window_start BIGINT NOT NULL,
 request_count BIGINT NOT NULL, expires_at DATETIME(6) NOT NULL,
 PRIMARY KEY (bucket_key, window_start), INDEX idx_checkout_rate_expiration (expires_at)
);
CREATE TABLE checkout_fraud_assessments (
 id BIGINT NOT NULL AUTO_INCREMENT, customer_id BIGINT NOT NULL, cart_id BIGINT NOT NULL,
 ip_hmac CHAR(64) NOT NULL, score INT NOT NULL, blocked BOOLEAN NOT NULL,
 reason_codes VARCHAR(255) NOT NULL, created_at DATETIME(6) NOT NULL, PRIMARY KEY (id),
 FOREIGN KEY (customer_id) REFERENCES checkout_customers(id), FOREIGN KEY (cart_id) REFERENCES checkout_carts(id),
 INDEX idx_checkout_fraud_customer (customer_id, created_at), INDEX idx_checkout_fraud_ip (ip_hmac, created_at)
);
CREATE TABLE checkout_ip_locations (
 ip_hmac CHAR(64) NOT NULL, country_code CHAR(2) NOT NULL, expires_at DATETIME(6) NOT NULL,
 PRIMARY KEY (ip_hmac)
);
