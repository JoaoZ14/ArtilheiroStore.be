-- Preço promocional (opcional). Mantém o preço principal (price).
ALTER TABLE product
    ADD COLUMN promo_price DECIMAL(19, 2) NULL;
