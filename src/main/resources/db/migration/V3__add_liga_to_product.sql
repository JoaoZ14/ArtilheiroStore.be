-- Adiciona coluna liga para filtrar produtos por liga
ALTER TABLE product ADD COLUMN liga VARCHAR(255) NOT NULL DEFAULT 'Brasileirão';
CREATE INDEX idx_product_liga ON product(liga);

-- Atualiza produtos de exemplo com ligas coerentes
UPDATE product SET liga = 'Brasileirão' WHERE team IN ('Flamengo', 'Corinthians');
UPDATE product SET liga = 'Seleção' WHERE team = 'Brasil';
