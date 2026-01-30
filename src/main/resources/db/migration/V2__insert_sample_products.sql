-- Produtos de exemplo para testes e integração com o frontend
INSERT INTO product (id, name, team, category, price, images, sizes, active, created_at) VALUES
(
    'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
    'Camisa Flamengo 23/24',
    'Flamengo',
    'Camisa',
    199.90,
    '["https://example.com/fla1.jpg", "https://example.com/fla2.jpg"]',
    '{"P": 5, "M": 10, "G": 8, "GG": 3}',
    true,
    CURRENT_TIMESTAMP
),
(
    'b2c3d4e5-f6a7-5b6c-9d0e-1f2a3b4c5d6e',
    'Camisa Corinthians 23/24',
    'Corinthians',
    'Camisa',
    189.90,
    '["https://example.com/cor1.jpg"]',
    '{"P": 3, "M": 7, "G": 5, "GG": 2}',
    true,
    CURRENT_TIMESTAMP
),
(
    'c3d4e5f6-a7b8-6c7d-0e1f-2a3b4c5d6e7f',
    'Camisa Brasil Away',
    'Brasil',
    'Camisa',
    249.90,
    '["https://example.com/bra1.jpg", "https://example.com/bra2.jpg"]',
    '{"M": 12, "G": 15, "GG": 6}',
    true,
    CURRENT_TIMESTAMP
);
