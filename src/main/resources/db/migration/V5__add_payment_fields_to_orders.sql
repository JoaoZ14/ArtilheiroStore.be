ALTER TABLE orders
    ADD COLUMN payment_preference_id VARCHAR(255),
    ADD COLUMN payment_id VARCHAR(50);
