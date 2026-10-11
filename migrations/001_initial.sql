CREATE TABLE users (
 id INTEGER PRIMARY KEY, nome TEXT NOT NULL, email TEXT NOT NULL UNIQUE,
 password_hash TEXT NOT NULL, perfil TEXT NOT NULL CHECK(perfil IN ('CLIENTE','ATENDENTE','COZINHA','GERENTE','ADMIN')),
 unidade_id INTEGER REFERENCES units(id), active INTEGER NOT NULL DEFAULT 1,
 consent INTEGER NOT NULL DEFAULT 0, consent_at TEXT, consent_version TEXT,
 points INTEGER NOT NULL DEFAULT 0 CHECK(points>=0), created_at TEXT NOT NULL
);
CREATE TABLE units (id INTEGER PRIMARY KEY, nome TEXT NOT NULL, endereco TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE products (id INTEGER PRIMARY KEY, nome TEXT NOT NULL, descricao TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE stocks (
 unidade_id INTEGER NOT NULL REFERENCES units(id), produto_id INTEGER NOT NULL REFERENCES products(id),
 quantidade INTEGER NOT NULL CHECK(quantidade>=0), preco_centavos INTEGER NOT NULL CHECK(preco_centavos>0),
 PRIMARY KEY(unidade_id,produto_id)
);
CREATE TABLE orders (
 id INTEGER PRIMARY KEY, cliente_id INTEGER NOT NULL REFERENCES users(id), unidade_id INTEGER NOT NULL REFERENCES units(id),
 canal_pedido TEXT NOT NULL CHECK(canal_pedido IN ('APP','TOTEM','BALCAO','PICKUP','WEB')),
 status TEXT NOT NULL CHECK(status IN ('AGUARDANDO_PAGAMENTO','EM_PREPARO','PRONTO','ENTREGUE','CANCELADO')),
 subtotal_centavos INTEGER NOT NULL, desconto_centavos INTEGER NOT NULL, total_centavos INTEGER NOT NULL CHECK(total_centavos>=0),
 pontos_resgate INTEGER NOT NULL DEFAULT 0, pontos_concedidos INTEGER NOT NULL DEFAULT 0,
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE TABLE order_items (
 id INTEGER PRIMARY KEY, pedido_id INTEGER NOT NULL REFERENCES orders(id), produto_id INTEGER NOT NULL REFERENCES products(id),
 nome_produto TEXT NOT NULL, quantidade INTEGER NOT NULL CHECK(quantidade>0), preco_centavos INTEGER NOT NULL CHECK(preco_centavos>0),
 UNIQUE(pedido_id,produto_id)
);
CREATE TABLE payments (
 id INTEGER PRIMARY KEY, pedido_id INTEGER NOT NULL REFERENCES orders(id),
 status TEXT NOT NULL CHECK(status IN ('APROVADO','NEGADO','ERRO','ESTORNADO')),
 valor_centavos INTEGER NOT NULL, provider_payload TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE TABLE stock_movements (
 id INTEGER PRIMARY KEY, unidade_id INTEGER NOT NULL, produto_id INTEGER NOT NULL, quantidade INTEGER NOT NULL,
 motivo TEXT NOT NULL, pedido_id INTEGER REFERENCES orders(id), actor_id INTEGER NOT NULL REFERENCES users(id), created_at TEXT NOT NULL,
 FOREIGN KEY(unidade_id,produto_id) REFERENCES stocks(unidade_id,produto_id)
);
CREATE TABLE loyalty_movements (
 id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), pedido_id INTEGER REFERENCES orders(id),
 quantidade INTEGER NOT NULL, motivo TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE TABLE audit (
 id INTEGER PRIMARY KEY, actor_id INTEGER NOT NULL REFERENCES users(id), acao TEXT NOT NULL,
 recurso TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE TABLE idempotency (
 actor_id INTEGER NOT NULL REFERENCES users(id), operacao TEXT NOT NULL, chave TEXT NOT NULL,
 fingerprint TEXT NOT NULL, recurso_id INTEGER NOT NULL, PRIMARY KEY(actor_id,operacao,chave)
);
CREATE INDEX idx_orders_unit_channel ON orders(unidade_id,canal_pedido,status);
CREATE INDEX idx_orders_client ON orders(cliente_id,id);
CREATE INDEX idx_payments_order ON payments(pedido_id);
CREATE INDEX idx_audit_actor ON audit(actor_id,id);
