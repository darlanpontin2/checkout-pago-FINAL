-- Catálogo mínimo para executar este checkout em um banco novo.
-- Ao integrar em ecommerce existente, usar o catálogo/migrations daquele sistema.
CREATE TABLE departments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(150) NOT NULL UNIQUE
);
CREATE TABLE sub_departments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(150) NOT NULL,
 department_id BIGINT NOT NULL,
 FOREIGN KEY (department_id) REFERENCES departments(id)
);
CREATE TABLE categories (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(150) NOT NULL,
 sub_department_id BIGINT NOT NULL,
 FOREIGN KEY (sub_department_id) REFERENCES sub_departments(id)
);
CREATE TABLE products (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 sku VARCHAR(36) NOT NULL UNIQUE,
 name VARCHAR(150) NOT NULL,
 price DECIMAL(8,2) NOT NULL,
 description VARCHAR(255) NOT NULL,
 category_id BIGINT NOT NULL,
 FOREIGN KEY (category_id) REFERENCES categories(id)
);
CREATE TABLE product_images (
 product_id BIGINT NOT NULL,
 url VARCHAR(255) NOT NULL,
 `key` VARCHAR(255) NOT NULL UNIQUE,
 image_order TINYINT NOT NULL DEFAULT 10,
 FOREIGN KEY (product_id) REFERENCES products(id),
 INDEX idx_product_images_product (product_id)
);
