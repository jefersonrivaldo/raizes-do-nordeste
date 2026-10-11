import concurrent.futures
import jwt
from app.infrastructure.database import transaction, migrate
from app.infrastructure.seed import seed


def pay(api, headers, id, scenario='APROVAR', key='payment-1'):
    return api.post(f'/pedidos/{id}/pagamentos', json={'cenario':scenario},
                    headers={**headers, 'Idempotency-Key':key})

def test_registration_login_and_no_secrets(api, auth):
    r = api.get('/usuarios/me', headers=auth['client'])
    assert r.status_code == 200
    assert r.json()['perfil'] == 'CLIENTE'
    assert 'senha' not in r.text and 'password_hash' not in r.text and 'email' not in r.text

def test_invalid_login(api, auth):
    r = api.post('/auth/login', json={'email':'cliente@example.com','senha':'errada'})
    assert r.status_code == 401
    assert r.json()['error'] == 'CREDENCIAIS_INVALIDAS'

def test_no_token(api):
    r = api.get('/pedidos')
    assert r.status_code == 401
    assert set(r.json()) == {'error','message','details','timestamp','path','requestId'}

def test_forbidden_role(api, auth):
    assert api.post('/unidades', json={'nome':'Nova unidade','endereco':'Rua nova, 100'}, headers=auth['client']).status_code == 403

def test_public_cannot_choose_role(api):
    r = api.post('/usuarios', json={'nome':'Pessoa','email':'p@example.com','senha':'SenhaTeste123','perfil':'ADMIN'})
    assert r.status_code == 422
    assert 'SenhaTeste123' not in r.text

def test_missing_channel(api, auth):
    r = api.post('/pedidos', json={'unidadeId':1,'itens':[{'produtoId':1,'quantidade':1}]},
                 headers={**auth['client'],'Idempotency-Key':'missing-channel'})
    assert r.status_code == 422
    assert any('canalPedido' in d['field'] for d in r.json()['details'])

def test_invalid_channel(api, order):
    assert order(canalPedido='WHATSAPP').status_code == 422
    assert api.get('/pedidos?canalPedido=OUTRO').status_code == 401

def test_stock_insufficient_rollback(api, auth, order):
    r = order(quantity=101)
    assert r.status_code == 409
    assert r.json()['error'] == 'ESTOQUE_INSUFICIENTE'
    with transaction() as db:
        assert db.execute('SELECT count(*) FROM orders').fetchone()[0] == 0
        assert db.execute('SELECT quantidade FROM stocks WHERE unidade_id=1 AND produto_id=1').fetchone()[0] == 100

def test_duplicate_items_rejected(api, order):
    assert order(itens=[{'produtoId':1,'quantidade':1},{'produtoId':1,'quantidade':1}]).status_code == 422

def test_order_idempotency_and_prices(api, auth, order):
    first = order()
    repeated = order()
    assert first.status_code == repeated.status_code == 201
    assert first.json()['id'] == repeated.json()['id']
    assert first.json()['totalCentavos'] == 5000
    assert order(quantity=3).status_code == 409
    menu = api.get('/unidades/1/cardapio').json()['items']
    assert menu[0]['disponivel'] == 98

def test_filter_channel_and_pagination(api, auth, order):
    order(canalPedido='TOTEM')
    r = api.get('/pedidos?canalPedido=TOTEM&limit=1&page=1', headers=auth['client'])
    assert r.status_code == 200 and len(r.json()['items']) == 1
    assert r.json()['items'][0]['canalPedido'] == 'TOTEM'
    assert api.get('/pedidos?canalPedido=APP', headers=auth['client']).json()['items'] == []
    assert api.get('/pedidos?limit=101', headers=auth['client']).status_code == 422

def test_payment_denial_and_retry(api, auth, order):
    id = order().json()['id']
    r = pay(api, auth['client'], id, 'NEGAR')
    assert r.status_code == 201 and r.json()['status'] == 'NEGADO'
    assert api.get(f'/pedidos/{id}', headers=auth['client']).json()['status'] == 'AGUARDANDO_PAGAMENTO'
    assert pay(api, auth['client'], id, 'APROVAR').status_code == 409
    assert pay(api, auth['client'], id, 'APROVAR','payment-2').json()['status'] == 'APROVADO'

def test_gateway_unavailable_then_retry(api, auth, order):
    id = order().json()['id']
    assert pay(api, auth['client'], id, 'INDISPONIVEL').json()['status'] == 'ERRO'
    assert pay(api, auth['client'], id, 'APROVAR','payment-2').json()['status'] == 'APROVADO'

def test_closed_flow_and_loyalty(api, auth, order):
    assert api.put('/fidelidade/consentimento', json={'consentimento':True}, headers=auth['client']).status_code == 200
    id = order(quantity=4).json()['id']
    r = pay(api, auth['client'], id)
    assert r.json()['status'] == 'APROVADO'
    assert pay(api, auth['client'], id).json()['id'] == r.json()['id']
    for status in ['PRONTO','ENTREGUE']:
        r = api.patch(f'/pedidos/{id}/status', json={'status':status}, headers=auth['admin'])
        assert r.status_code == 200 and r.json()['status'] == status
    assert api.get('/fidelidade', headers=auth['client']).json()['saldo'] == 100
    assert api.patch(f'/pedidos/{id}/status', json={'status':'ENTREGUE'}, headers=auth['admin']).status_code == 409
    assert api.get('/fidelidade', headers=auth['client']).json()['saldo'] == 100
    r = order(key='resgate-2', pontosResgate=100)
    assert r.status_code == 201 and r.json()['totalCentavos'] == 4000
    api.patch(f"/pedidos/{r.json()['id']}/status", json={'status':'CANCELADO'}, headers=auth['client'])
    assert api.get('/fidelidade', headers=auth['client']).json()['saldo'] == 100
    api.put('/fidelidade/consentimento', json={'consentimento':False}, headers=auth['client'])
    assert api.get('/fidelidade', headers=auth['client']).json()['saldo'] == 0

def test_no_loyalty_without_consent(api, auth, order):
    assert order(pontosResgate=100).status_code == 409
    id = order().json()['id']
    pay(api, auth['client'], id)
    for status in ['PRONTO','ENTREGUE']:
        api.patch(f'/pedidos/{id}/status', json={'status':status}, headers=auth['admin'])
    assert api.get('/fidelidade', headers=auth['client']).json()['saldo'] == 0

def test_cancel_restores_stock_once(api, auth, order):
    id = order().json()['id']
    assert api.patch(f'/pedidos/{id}/status', json={'status':'CANCELADO'}, headers=auth['client']).status_code == 200
    assert api.patch(f'/pedidos/{id}/status', json={'status':'CANCELADO'}, headers=auth['admin']).status_code == 409
    assert api.get('/unidades/1/cardapio').json()['items'][0]['disponivel'] == 100
    assert pay(api, auth['client'], id).status_code == 409

def test_paid_cancel_mock_refund(api, auth, order):
    id = order().json()['id']
    pay(api, auth['client'], id)
    assert api.patch(f'/pedidos/{id}/status', json={'status':'CANCELADO'}, headers=auth['client']).status_code == 403
    assert api.patch(f'/pedidos/{id}/status', json={'status':'CANCELADO'}, headers=auth['admin']).status_code == 200
    assert api.get(f'/pedidos/{id}/pagamentos', headers=auth['client']).json()['items'][0]['status'] == 'ESTORNADO'

def test_invalid_transition(api, auth, order):
    id = order().json()['id']
    assert api.patch(f'/pedidos/{id}/status', json={'status':'ENTREGUE'}, headers=auth['admin']).status_code == 409

def test_other_customer_cannot_access(api, auth, order):
    id = order().json()['id']
    auth['register']('outra@example.com')
    other = auth['login']('outra@example.com')
    assert api.get(f'/pedidos/{id}', headers=other).status_code == 403
    assert pay(api, other, id).status_code == 403
    assert api.get('/pedidos', headers=other).json()['items'] == []

def test_employee_unit_scope_and_kitchen(api, auth, order):
    id = order().json()['id']
    worker = auth['register']('cozinha@example.com')
    assert api.put(f'/usuarios/{worker}/perfil', json={'perfil':'COZINHA','unidadeId':2}, headers=auth['admin']).status_code == 200
    kitchen = auth['login']('cozinha@example.com')
    assert api.get(f'/pedidos/{id}', headers=kitchen).status_code == 403
    api.put(f'/usuarios/{worker}/perfil', json={'perfil':'COZINHA','unidadeId':1}, headers=auth['admin'])
    pay(api, auth['client'], id)
    assert api.patch(f'/pedidos/{id}/status', json={'status':'PRONTO'}, headers=kitchen).status_code == 200
    assert api.patch(f'/pedidos/{id}/status', json={'status':'ENTREGUE'}, headers=kitchen).status_code == 403

def test_stock_management_by_unit(api, auth):
    worker = auth['register']('gerente@example.com')
    api.put(f'/usuarios/{worker}/perfil', json={'perfil':'GERENTE','unidadeId':1}, headers=auth['admin'])
    manager = auth['login']('gerente@example.com')
    body = {'produtoId':1,'quantidade':10,'motivo':'Reposição'}
    assert api.post('/unidades/2/estoque/movimentos', json=body, headers=manager).status_code == 403
    assert api.post('/unidades/1/estoque/movimentos', json=body, headers=manager).json()['quantidade'] == 110
    assert api.post('/unidades/1/estoque/movimentos', json={**body,'quantidade':-111}, headers=manager).status_code == 409

def test_coupon_and_missing_resource(api, auth, order):
    assert order(cupom='NORDESTE10').json()['totalCentavos'] == 4500
    assert order(quantity=1, key='bad-coupon', cupom='NORDESTE10').status_code == 409
    assert order(unidadeId=999,key='missing-unit').status_code == 404

def test_anonymization_disables_existing_token(api, auth, order):
    id = order().json()['id']
    assert api.delete('/usuarios/me', headers=auth['client']).status_code == 409
    api.patch(f'/pedidos/{id}/status', json={'status':'CANCELADO'}, headers=auth['client'])
    assert api.delete('/usuarios/me', headers=auth['client']).status_code == 204
    assert api.get('/usuarios/me', headers=auth['client']).status_code == 401
    with transaction() as db:
        row = db.execute('SELECT * FROM users WHERE id=?',(auth['id'],)).fetchone()
        assert row['nome'] == 'Anonimizado' and row['email'].startswith('anon-')

def test_expired_and_tampered_token(api, auth):
    expired = jwt.encode({'sub':str(auth['id']),'iat':1,'exp':2,'iss':'raizes-do-nordeste','aud':'raizes-api'},
                         'only-tests-secret-at-least-32-characters-long', algorithm='HS256')
    assert api.get('/usuarios/me', headers={'Authorization':'Bearer '+expired}).status_code == 401
    assert api.get('/usuarios/me', headers={'Authorization':'Bearer not-a-token'}).status_code == 401

def test_concurrent_order_cannot_oversell(api, auth, order):
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda k: order(quantity=60,key=k), ['concurrent-1','concurrent-2']))
    assert sorted(r.status_code for r in responses) == [201,409]
    assert api.get('/unidades/1/cardapio').json()['items'][0]['disponivel'] == 40

def test_concurrent_same_key_only_one_order(api, auth, order):
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _:order(), range(2)))
    assert [r.status_code for r in responses] == [201,201]
    assert responses[0].json()['id'] == responses[1].json()['id']
    assert api.get('/unidades/1/cardapio').json()['items'][0]['disponivel'] == 98

def test_migrations_seed_repeatability(api, auth, order):
    order()
    migrate(); seed(); migrate()
    assert api.get('/unidades/1/cardapio').json()['items'][0]['disponivel'] == 98
    with transaction() as db:
        assert db.execute('SELECT count(*) FROM schema_migrations').fetchone()[0] == 1
        assert db.execute("SELECT count(*) FROM users WHERE perfil='ADMIN'").fetchone()[0] == 1

def test_audit_and_openapi(api, auth, order):
    order()
    events = api.get('/auditoria', headers=auth['admin']).json()['items']
    assert any(e['acao'] == 'PEDIDO_CRIADO' for e in events)
    assert 'password_hash' not in str(events) and 'SenhaTeste123' not in str(events)
    schema = api.get('/openapi.json').json()
    assert 'canalPedido' in schema['components']['schemas']['Pedido']['required']
    assert '/pedidos/{id}/pagamentos' in schema['paths']
    assert api.get('/health').json() == {'status':'ok','database':'ok'}
