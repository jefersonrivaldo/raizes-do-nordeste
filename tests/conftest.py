import pytest
from fastapi.testclient import TestClient
from app.infrastructure.database import migrate, transaction
from app.infrastructure.seed import seed
from app.api.main import app

@pytest.fixture
def api(tmp_path, monkeypatch):
    monkeypatch.setenv('DATABASE_PATH', str(tmp_path / 'test.sqlite3'))
    monkeypatch.setenv('JWT_SECRET', 'only-tests-secret-at-least-32-characters-long')
    monkeypatch.setenv('SEED_ADMIN_PASSWORD', 'AdminTeste12345')
    migrate()
    seed()
    with TestClient(app) as client:
        yield client

@pytest.fixture
def auth(api):
    def register(email='cliente@example.com'):
        r = api.post('/usuarios', json={'nome':'Maria de estudo','email':email,'senha':'SenhaTeste123'})
        assert r.status_code == 201, r.text
        return r.json()['id']
    def login(email='cliente@example.com', password='SenhaTeste123'):
        r = api.post('/auth/login', json={'email':email, 'senha':password})
        assert r.status_code == 200, r.text
        return {'Authorization': 'Bearer ' + r.json()['accessToken']}
    id = register()
    return {'id':id, 'client':login(), 'admin':login('admin@raizes.local','AdminTeste12345'),
            'register':register, 'login':login}

@pytest.fixture
def order(api, auth):
    def create(quantity=2, key='test-order-1', **kwargs):
        data = {'unidadeId':1,'canalPedido':'APP','itens':[{'produtoId':1,'quantidade':quantity}], **kwargs}
        return api.post('/pedidos', json=data, headers={**auth['client'], 'Idempotency-Key':key})
    return create
