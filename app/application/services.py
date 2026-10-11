"""Casos de uso transacionais; SQL parametrizado e gateway desacoplado."""
import hashlib
import json
import sqlite3
from app.domain.rules import require, totals, transition, BusinessError
from app.infrastructure.database import transaction, now
from app.infrastructure.security import hash_password, verify_password, token
from app.infrastructure.payment import request_payment


def audit(db, actor, action, resource):
    db.execute('INSERT INTO audit(actor_id,acao,recurso,created_at) VALUES (?,?,?,?)',
               (actor, action, str(resource), now()))

def get(db, table, id):
    # table vem apenas de constantes internas, nunca de entradas HTTP.
    row = db.execute(f'SELECT * FROM {table} WHERE id=?', (id,)).fetchone()
    require(row is not None, 'NAO_ENCONTRADO', 'Recurso não encontrado.', 404)
    return row

def public_user(row):
    return {'id': row['id'], 'nome': row['nome'], 'perfil': row['perfil'],
            'unidadeId': row['unidade_id']}

def register(data):
    with transaction() as db:
        try:
            id = db.execute('INSERT INTO users(nome,email,password_hash,perfil,created_at) VALUES (?,?,?,?,?)',
                (data.nome, data.email.lower(), hash_password(data.senha), 'CLIENTE', now())).lastrowid
        except sqlite3.IntegrityError:
            raise BusinessError('EMAIL_EM_USO', 'E-mail já cadastrado.')
        audit(db, id, 'CADASTRO', id)
        return public_user(get(db, 'users', id))

# Hash fictício reduz diferença de tempo entre usuário ausente e senha incorreta.
DUMMY_HASH = hash_password('not-a-user-password')
def login(data):
    with transaction() as db:
        row = db.execute('SELECT * FROM users WHERE email=? AND active=1', (data.email.lower(),)).fetchone()
        valid = verify_password(data.senha, row['password_hash'] if row else DUMMY_HASH)
        require(row is not None and valid, 'CREDENCIAIS_INVALIDAS', 'E-mail ou senha inválidos.', 401)
        access, ttl = token(row['id'])
        audit(db, row['id'], 'LOGIN', row['id'])
        return {'accessToken': access, 'tokenType': 'Bearer', 'expiresIn': ttl, 'user': public_user(row)}

def unit_access(user, unit_id):
    require(user['perfil'] == 'ADMIN' or user['unidade_id'] == unit_id,
            'SEM_PERMISSAO', 'Sem acesso à unidade.', 403)

def order_access(user, order):
    if user['perfil'] == 'CLIENTE':
        require(order['cliente_id'] == user['id'], 'SEM_PERMISSAO', 'Sem acesso ao pedido.', 403)
    else:
        unit_access(user, order['unidade_id'])

def fingerprint(data):
    return hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()

def replay(db, user, operation, key, digest):
    row = db.execute('SELECT * FROM idempotency WHERE actor_id=? AND operacao=? AND chave=?',
                     (user['id'], operation, key)).fetchone()
    if row:
        require(row['fingerprint'] == digest, 'IDEMPOTENCIA_CONFLITO', 'Chave já utilizada com outro conteúdo.')
        return row['recurso_id']

def remember(db, user, operation, key, digest, id):
    db.execute('INSERT INTO idempotency VALUES (?,?,?,?,?)', (user['id'], operation, key, digest, id))

def order_json(db, id):
    r = get(db, 'orders', id)
    items = db.execute('SELECT * FROM order_items WHERE pedido_id=?', (id,)).fetchall()
    return {'id': id, 'clienteId': r['cliente_id'], 'unidadeId': r['unidade_id'], 'canalPedido': r['canal_pedido'],
            'status': r['status'], 'subtotalCentavos': r['subtotal_centavos'], 'descontoCentavos': r['desconto_centavos'],
            'totalCentavos': r['total_centavos'], 'pontosResgate': r['pontos_resgate'], 'createdAt': r['created_at'],
            'itens': [{'produtoId': i['produto_id'], 'nome': i['nome_produto'], 'quantidade': i['quantidade'],
                       'precoUnitarioCentavos': i['preco_centavos']} for i in items]}

def stock_move(db, unit, product, quantity, reason, actor, order=None):
    cursor = db.execute('UPDATE stocks SET quantidade=quantidade+? WHERE unidade_id=? AND produto_id=? AND quantidade+?>=0',
                       (quantity, unit, product, quantity))
    require(cursor.rowcount == 1, 'ESTOQUE_INSUFICIENTE', 'Estoque inexistente ou quantidade insuficiente.')
    db.execute('INSERT INTO stock_movements(unidade_id,produto_id,quantidade,motivo,pedido_id,actor_id,created_at) VALUES (?,?,?,?,?,?,?)',
               (unit, product, quantity, reason, order, actor, now()))

def loyalty_move(db, user, quantity, reason, order=None):
    cursor = db.execute('UPDATE users SET points=points+? WHERE id=? AND points+?>=0', (quantity, user, quantity))
    require(cursor.rowcount == 1, 'PONTOS_INSUFICIENTES', 'Saldo insuficiente de pontos.')
    db.execute('INSERT INTO loyalty_movements(user_id,pedido_id,quantidade,motivo,created_at) VALUES (?,?,?,?,?)',
               (user, order, quantity, reason, now()))

def create_order(user, data, key):
    payload = data.model_dump(mode='json')
    digest = fingerprint(payload)
    with transaction() as db:
        old = replay(db, user, 'PEDIDO', key, digest)
        if old:
            return order_json(db, old)
        unit = get(db, 'units', data.unidadeId)
        require(unit['active'], 'UNIDADE_INATIVA', 'Unidade inativa.')
        customer_id = data.clienteId or user['id']
        if user['perfil'] == 'CLIENTE':
            require(customer_id == user['id'], 'SEM_PERMISSAO', 'Cliente só pode criar pedidos próprios.', 403)
        else:
            unit_access(user, data.unidadeId)
        customer = get(db, 'users', customer_id)
        require(customer['perfil'] == 'CLIENTE' and customer['active'], 'CLIENTE_INVALIDO', 'Cliente inválido.')
        require(len({i.produtoId for i in data.itens}) == len(data.itens), 'ITEM_DUPLICADO', 'Agrupe produtos repetidos.', 422)
        entries, subtotal = [], 0
        for item in data.itens:
            product = get(db, 'products', item.produtoId)
            require(product['active'], 'PRODUTO_INATIVO', 'Produto inativo.')
            stock = db.execute('SELECT * FROM stocks WHERE unidade_id=? AND produto_id=?', (data.unidadeId, item.produtoId)).fetchone()
            require(stock is not None and stock['quantidade'] >= item.quantidade, 'ESTOQUE_INSUFICIENTE', 'Estoque insuficiente na unidade.')
            entries.append((item, product, stock['preco_centavos']))
            subtotal += stock['preco_centavos'] * item.quantidade
        if data.pontosResgate:
            require(customer['consent'], 'CONSENTIMENTO_NECESSARIO', 'Fidelidade exige consentimento ativo.')
            require(customer['points'] >= data.pontosResgate, 'PONTOS_INSUFICIENTES', 'Saldo insuficiente de pontos.')
        total, discount = totals(subtotal, data.cupom, data.pontosResgate)
        date = now()
        id = db.execute('INSERT INTO orders(cliente_id,unidade_id,canal_pedido,status,subtotal_centavos,desconto_centavos,total_centavos,pontos_resgate,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)',
             (customer_id, data.unidadeId, data.canalPedido.value, 'AGUARDANDO_PAGAMENTO', subtotal, discount, total, data.pontosResgate, date, date)).lastrowid
        for item, product, price in entries:
            db.execute('INSERT INTO order_items(pedido_id,produto_id,nome_produto,quantidade,preco_centavos) VALUES (?,?,?,?,?)',
                       (id, item.produtoId, product['nome'], item.quantidade, price))
            stock_move(db, data.unidadeId, item.produtoId, -item.quantidade, 'VENDA', user['id'], id)
        if data.pontosResgate:
            loyalty_move(db, customer_id, -data.pontosResgate, 'RESGATE', id)
        audit(db, user['id'], 'PEDIDO_CRIADO', id)
        remember(db, user, 'PEDIDO', key, digest, id)
        return order_json(db, id)

def payment_json(row):
    return {'id': row['id'], 'pedidoId': row['pedido_id'], 'status': row['status'],
            'valorCentavos': row['valor_centavos'], 'payload': json.loads(row['provider_payload'])}

def pay(user, order_id, scenario, key):
    digest = fingerprint({'pedidoId': order_id, 'cenario': scenario})
    with transaction() as db:
        order = get(db, 'orders', order_id)
        order_access(user, order)
        old = replay(db, user, 'PAGAMENTO', key, digest)
        if old:
            return payment_json(get(db, 'payments', old))
        require(order['status'] == 'AGUARDANDO_PAGAMENTO', 'PAGAMENTO_INVALIDO', 'Pedido não aguarda pagamento.')
        result = request_payment(order_id, order['total_centavos'], scenario)
        id = db.execute('INSERT INTO payments(pedido_id,status,valor_centavos,provider_payload,created_at) VALUES (?,?,?,?,?)',
            (order_id, result['status'], order['total_centavos'], json.dumps(result), now())).lastrowid
        if result['status'] == 'APROVADO':
            db.execute('UPDATE orders SET status=?,updated_at=? WHERE id=?', ('EM_PREPARO', now(), order_id))
        remember(db, user, 'PAGAMENTO', key, digest, id)
        audit(db, user['id'], 'PAGAMENTO_' + result['status'], order_id)
        return payment_json(get(db, 'payments', id))

def update_status(user, order_id, target):
    with transaction() as db:
        order = get(db, 'orders', order_id)
        order_access(user, order)
        if user['perfil'] == 'CLIENTE':
            require(target == 'CANCELADO' and order['status'] == 'AGUARDANDO_PAGAMENTO',
                    'SEM_PERMISSAO', 'Cliente pode cancelar apenas pedidos ainda não pagos.', 403)
        elif user['perfil'] == 'COZINHA':
            require(target == 'PRONTO', 'SEM_PERMISSAO', 'Cozinha pode apenas marcar como pronto.', 403)
        transition(order['status'], target)
        if target == 'CANCELADO':
            for item in db.execute('SELECT * FROM order_items WHERE pedido_id=?', (order_id,)).fetchall():
                stock_move(db, order['unidade_id'], item['produto_id'], item['quantidade'], 'CANCELAMENTO', user['id'], order_id)
            customer = get(db, 'users', order['cliente_id'])
            if order['pontos_resgate'] and customer['consent']:
                loyalty_move(db, customer['id'], order['pontos_resgate'], 'DEVOLUCAO', order_id)
            db.execute("UPDATE payments SET status='ESTORNADO' WHERE pedido_id=? AND status='APROVADO'", (order_id,))
        if target == 'ENTREGUE':
            customer = get(db, 'users', order['cliente_id'])
            points = order['total_centavos'] // 100 if customer['consent'] else 0
            if points:
                loyalty_move(db, customer['id'], points, 'COMPRA', order_id)
            db.execute('UPDATE orders SET pontos_concedidos=? WHERE id=?', (points, order_id))
        db.execute('UPDATE orders SET status=?,updated_at=? WHERE id=?', (target, now(), order_id))
        audit(db, user['id'], 'STATUS_' + target, order_id)
        return order_json(db, order_id)

def consent(user, enabled):
    with transaction() as db:
        row = get(db, 'users', user['id'])
        if not enabled and row['points']:
            loyalty_move(db, user['id'], -row['points'], 'REVOGACAO')
        db.execute('UPDATE users SET consent=?,consent_at=?,consent_version=? WHERE id=?',
                   (int(enabled), now(), '1.0', user['id']))
        audit(db, user['id'], 'CONSENTIMENTO_ATIVO' if enabled else 'CONSENTIMENTO_REVOGADO', user['id'])
        return {'consentimento': enabled, 'versao': '1.0'}

def anonymize(user):
    with transaction() as db:
        active = db.execute("SELECT 1 FROM orders WHERE cliente_id=? AND status NOT IN ('ENTREGUE','CANCELADO')", (user['id'],)).fetchone()
        require(active is None, 'PEDIDOS_EM_ABERTO', 'Finalize ou cancele pedidos antes da anonimização.')
        row = get(db, 'users', user['id'])
        if row['points']:
            loyalty_move(db, user['id'], -row['points'], 'ANONIMIZACAO')
        db.execute('UPDATE users SET nome=?,email=?,password_hash=?,active=0,consent=0,consent_at=?,consent_version=? WHERE id=?',
                  ('Anonimizado', f"anon-{user['id']}@invalid.local", hash_password(__import__('secrets').token_hex(32)), now(), '1.0', user['id']))
        audit(db, user['id'], 'ANONIMIZACAO', user['id'])
