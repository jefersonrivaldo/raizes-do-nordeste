"""Seed idempotente; não altera usuários nem estoques já existentes."""
import os
from app.infrastructure.database import migrate, transaction, now
from app.infrastructure.security import hash_password

def seed():
    migrate()
    with transaction() as db:
        if not db.execute("SELECT 1 FROM users WHERE email='admin@raizes.local'").fetchone():
            password = os.getenv('SEED_ADMIN_PASSWORD', '')
            if len(password) < 12 or not any(c.isalpha() for c in password) or not any(c.isdigit() for c in password):
                raise RuntimeError('Defina SEED_ADMIN_PASSWORD com 12+ caracteres, letras e números.')
            db.execute('INSERT INTO users(nome,email,password_hash,perfil,created_at) VALUES (?,?,?,?,?)',
                       ('Administrador de estudo', 'admin@raizes.local', hash_password(password), 'ADMIN', now()))
        for id, name, address in [(1, 'Recife Centro', 'Rua de estudo, 100 — Recife'), (2, 'Olinda', 'Rua de estudo, 200 — Olinda')]:
            db.execute('INSERT OR IGNORE INTO units(id,nome,endereco) VALUES (?,?,?)', (id, name, address))
        for id, name, price in [(1, 'Baião de dois', 2500), (2, 'Tapioca de queijo', 1200), (3, 'Suco de caju', 800)]:
            db.execute('INSERT OR IGNORE INTO products(id,nome,descricao) VALUES (?,?,?)', (id, name, 'Produto de demonstração'))
            for unit in [1, 2]:
                cursor = db.execute('INSERT OR IGNORE INTO stocks VALUES (?,?,100,?)', (unit, id, price))
                if cursor.rowcount:
                    actor = db.execute("SELECT id FROM users WHERE email='admin@raizes.local'").fetchone()['id']
                    db.execute('INSERT INTO stock_movements(unidade_id,produto_id,quantidade,motivo,actor_id,created_at) VALUES (?,?,100,?,?,?)',
                               (unit, id, 'SEED', actor, now()))
    print('Seed aplicado sem redefinir saldos existentes.')

if __name__ == '__main__':
    seed()
