from contextlib import asynccontextmanager
from typing import Annotated
from uuid import uuid4
import logging
import sqlite3
import jwt
from fastapi import FastAPI, Depends, Header, Query, Response, Request
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException
from fastapi.responses import JSONResponse
from app.api.schemas import (Cadastro, Login, Unidade, Produto, Vinculo, Estoque, Pedido, Pagamento,
    MudancaStatus, Consentimento, ErrorResponse, UsuarioResponse, LoginResponse, PedidoResponse, PagamentoResponse)
from app.api.schemas import (PageResponse, UnidadeResponse, ProdutoResponse, EstoqueResponse, CardapioResponse,
    FidelidadeResponse, ConsentimentoResponse, AuditoriaResponse, HealthResponse)
from app.domain.rules import BusinessError, require, Canal, Status
from app.infrastructure.database import transaction, now
from app.infrastructure.security import secret, decode
from app.application import services as s

@asynccontextmanager
async def lifespan(app):
    secret()
    with transaction() as db:
        db.execute('SELECT version FROM schema_migrations LIMIT 1').fetchone()
        db.execute('SELECT id FROM users LIMIT 1').fetchone()
    yield

ERRORS = {code: {'model': ErrorResponse, 'description': description} for code, description in
          [(401, 'Token ausente, inválido ou expirado'), (403, 'Perfil ou unidade sem permissão'),
           (404, 'Recurso inexistente'), (409, 'Conflito de regra de negócio'), (422, 'Contrato inválido'),
           (503, 'Banco temporariamente ocupado')]}
app = FastAPI(title='Raízes do Nordeste', version='1.0.0', lifespan=lifespan,
    description='API acadêmica multicanal. Valores em centavos. Pagamento exclusivamente MOCK. '
                'Cadastre um cliente, faça login e use Bearer token em Authorize. Rotas operacionais '
                'exigem vínculo com a unidade, exceto ADMIN. Listagens são paginadas.', responses=ERRORS)

@app.middleware('http')
async def request_id(request: Request, call_next):
    request.state.request_id = str(uuid4())
    response = await call_next(request)
    response.headers['X-Request-ID'] = request.state.request_id
    response.headers['Cache-Control'] = 'no-store'
    response.headers['X-Content-Type-Options'] = 'nosniff'
    return response

def error(request, code, message, status, details=None):
    return JSONResponse(status_code=status, content={'error': code, 'message': message, 'details': details or [],
         'timestamp': now(), 'path': request.url.path, 'requestId': getattr(request.state, 'request_id', str(uuid4()))})

@app.exception_handler(BusinessError)
async def business_error(request, exc):
    return error(request, exc.code, exc.message, exc.status, exc.details)

@app.exception_handler(RequestValidationError)
async def validation_error(request, exc):
    # Não incluir input: a validação pode conter senha ou outros dados pessoais.
    details = [{'field': '.'.join(map(str, e['loc'])), 'issue': e['msg']} for e in exc.errors()]
    return error(request, 'VALIDACAO', 'Dados inválidos.', 422, details)

@app.exception_handler(HTTPException)
async def http_error(request, exc):
    return error(request, 'HTTP_ERROR', str(exc.detail), exc.status_code)

@app.exception_handler(sqlite3.OperationalError)
async def database_error(request, exc):
    logging.getLogger('raizes').error('Falha operacional no banco; requestId=%s', getattr(request.state, 'request_id', ''))
    return error(request, 'BANCO_INDISPONIVEL', 'Banco indisponível; tente novamente.', 503)

@app.exception_handler(Exception)
async def unexpected_error(request, exc):
    logging.getLogger('raizes').error('Falha interna; requestId=%s tipo=%s', getattr(request.state, 'request_id', ''), type(exc).__name__)
    return error(request, 'ERRO_INTERNO', 'Erro interno.', 500)

bearer = HTTPBearer(auto_error=False)
def current_user(credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)]):
    require(credentials is not None and credentials.scheme.lower() == 'bearer', 'NAO_AUTENTICADO', 'Informe Bearer token.', 401)
    try:
        claims = decode(credentials.credentials)
        id = int(claims['sub'])
    except (jwt.InvalidTokenError, ValueError, KeyError):
        raise BusinessError('TOKEN_INVALIDO', 'Token inválido ou expirado.', 401)
    with transaction() as db:
        row = db.execute('SELECT * FROM users WHERE id=? AND active=1', (id,)).fetchone()
        require(row is not None, 'TOKEN_INVALIDO', 'Usuário indisponível.', 401)
        return dict(row)

def roles(*allowed):
    def permission(user=Depends(current_user)):
        require(user['perfil'] in allowed, 'SEM_PERMISSAO', 'Perfil sem permissão.', 403)
        return user
    return permission

User = Annotated[dict, Depends(current_user)]
Admin = Annotated[dict, Depends(roles('ADMIN'))]
Manager = Annotated[dict, Depends(roles('ADMIN', 'GERENTE'))]
Buyer = Annotated[dict, Depends(roles('CLIENTE', 'ATENDENTE', 'GERENTE', 'ADMIN'))]
Client = Annotated[dict, Depends(roles('CLIENTE'))]
Key = Annotated[str, Header(alias='Idempotency-Key', min_length=8, max_length=100, pattern=r'^[A-Za-z0-9_.-]+$')]
Page = Annotated[int, Query(ge=1)]
Limit = Annotated[int, Query(ge=1, le=100)]

def page_result(rows, page, limit):
    return {'items': rows, 'page': page, 'limit': limit}

def resource_list(db, table, page, limit):
    return [dict(r) for r in db.execute(f'SELECT * FROM {table} ORDER BY id LIMIT ? OFFSET ?', (limit, (page-1)*limit))]

@app.get('/health', response_model=HealthResponse, tags=['Operação'], summary='Verificar API e banco')
def health():
    with transaction() as db:
        db.execute('SELECT id FROM units LIMIT 1').fetchone()
    return {'status': 'ok', 'database': 'ok'}

@app.post('/usuarios', status_code=201, response_model=UsuarioResponse, tags=['Usuários'], summary='Cadastrar cliente público')
def register(data: Cadastro):
    return s.register(data)

@app.post('/auth/login', response_model=LoginResponse, tags=['Autenticação'], summary='Autenticar e emitir JWT')
def login(data: Login):
    return s.login(data)

@app.get('/usuarios/me', response_model=UsuarioResponse, tags=['Usuários'], summary='Consultar perfil próprio (JWT)')
def me(user: User):
    with transaction() as db:
        s.audit(db, user['id'], 'PERFIL_CONSULTADO', user['id'])
    return s.public_user(user)

@app.delete('/usuarios/me', status_code=204, tags=['Usuários'], summary='Anonimizar cliente (CLIENTE, sem pedidos em aberto)')
def anonymize(user: Client):
    s.anonymize(user)
    return Response(status_code=204)

@app.put('/usuarios/{id}/perfil', response_model=UsuarioResponse, tags=['Usuários'], summary='Definir perfil e unidade (ADMIN)')
def profile(id: int, data: Vinculo, user: Admin):
    require(id != user['id'], 'PERFIL_PROPRIO', 'Administrador não pode alterar seu próprio perfil.')
    with transaction() as db:
        target = s.get(db, 'users', id)
        require(target['active'], 'USUARIO_INATIVO', 'Usuário inativo.')
        require(data.perfil.value in ('ADMIN','CLIENTE') or data.unidadeId is not None,
                'UNIDADE_OBRIGATORIA', 'Perfil operacional exige unidade.', 422)
        require(data.perfil.value not in ('ADMIN','CLIENTE') or data.unidadeId is None,
                'UNIDADE_INVALIDA', 'ADMIN e CLIENTE não possuem vínculo operacional.', 422)
        require(not db.execute('SELECT 1 FROM orders WHERE cliente_id=? LIMIT 1', (id,)).fetchone() or data.perfil.value == 'CLIENTE',
                'PERFIL_COM_PEDIDOS', 'Usuário com pedidos deve manter perfil CLIENTE.')
        if data.unidadeId:
            s.get(db, 'units', data.unidadeId)
        db.execute('UPDATE users SET perfil=?,unidade_id=? WHERE id=?', (data.perfil.value, data.unidadeId, id))
        s.audit(db, user['id'], 'PERFIL_ALTERADO', id)
        return s.public_user(s.get(db, 'users', id))

@app.get('/unidades', response_model=PageResponse[UnidadeResponse], tags=['Unidades'], summary='Listar unidades (público)')
def units(page: Page = 1, limit: Limit = 20):
    with transaction() as db:
        return page_result(resource_list(db, 'units', page, limit), page, limit)

@app.post('/unidades', response_model=UnidadeResponse, status_code=201, tags=['Unidades'], summary='Cadastrar unidade (ADMIN)')
def new_unit(data: Unidade, user: Admin):
    with transaction() as db:
        id = db.execute('INSERT INTO units(nome,endereco,active) VALUES (?,?,?)', (data.nome, data.endereco, data.ativo)).lastrowid
        s.audit(db, user['id'], 'UNIDADE_CRIADA', id)
        return dict(s.get(db, 'units', id))

@app.put('/unidades/{id}', response_model=UnidadeResponse, tags=['Unidades'], summary='Atualizar unidade (ADMIN ou GERENTE da unidade)')
def update_unit(id: int, data: Unidade, user: Manager):
    s.unit_access(user, id)
    with transaction() as db:
        s.get(db, 'units', id)
        db.execute('UPDATE units SET nome=?,endereco=?,active=? WHERE id=?', (data.nome, data.endereco, data.ativo, id))
        s.audit(db, user['id'], 'UNIDADE_ALTERADA', id)
        return dict(s.get(db, 'units', id))

@app.get('/produtos', response_model=PageResponse[ProdutoResponse], tags=['Produtos'], summary='Listar catálogo (público)')
def products(page: Page = 1, limit: Limit = 20):
    with transaction() as db:
        return page_result(resource_list(db, 'products', page, limit), page, limit)

@app.post('/produtos', response_model=ProdutoResponse, status_code=201, tags=['Produtos'], summary='Cadastrar produto no catálogo global (ADMIN)')
def new_product(data: Produto, user: Admin):
    with transaction() as db:
        id = db.execute('INSERT INTO products(nome,descricao,active) VALUES (?,?,?)', (data.nome, data.descricao, data.ativo)).lastrowid
        s.audit(db, user['id'], 'PRODUTO_CRIADO', id)
        return dict(s.get(db, 'products', id))

@app.put('/produtos/{id}', response_model=ProdutoResponse, tags=['Produtos'], summary='Atualizar produto global (ADMIN)')
def update_product(id: int, data: Produto, user: Admin):
    with transaction() as db:
        s.get(db, 'products', id)
        db.execute('UPDATE products SET nome=?,descricao=?,active=? WHERE id=?', (data.nome, data.descricao, data.ativo, id))
        s.audit(db, user['id'], 'PRODUTO_ALTERADO', id)
        return dict(s.get(db, 'products', id))

@app.get('/unidades/{id}/cardapio', response_model=PageResponse[CardapioResponse], tags=['Cardápio'], summary='Consultar preços e disponibilidade por unidade (público)')
def menu(id: int, page: Page = 1, limit: Limit = 20):
    with transaction() as db:
        unit = s.get(db, 'units', id)
        require(unit['active'], 'UNIDADE_INATIVA', 'Unidade inativa.')
        rows = db.execute('SELECT p.id AS produtoId,p.nome,p.descricao,s.preco_centavos AS precoCentavos,s.quantidade AS disponivel FROM stocks s JOIN products p ON p.id=s.produto_id WHERE s.unidade_id=? AND p.active=1 ORDER BY p.id LIMIT ? OFFSET ?', (id, limit, (page-1)*limit))
        return page_result([dict(r) for r in rows], page, limit)

@app.get('/unidades/{id}/estoque', response_model=PageResponse[EstoqueResponse], tags=['Estoque'], summary='Consultar estoque próprio (ADMIN ou GERENTE)')
def stock(id: int, user: Manager, page: Page = 1, limit: Limit = 20):
    s.unit_access(user, id)
    with transaction() as db:
        s.get(db, 'units', id)
        rows = db.execute('SELECT produto_id AS produtoId,quantidade,preco_centavos AS precoCentavos FROM stocks WHERE unidade_id=? ORDER BY produto_id LIMIT ? OFFSET ?', (id, limit, (page-1)*limit))
        return page_result([dict(r) for r in rows], page, limit)

@app.post('/unidades/{id}/estoque/movimentos', response_model=EstoqueResponse, status_code=201, tags=['Estoque'], summary='Movimentar estoque e definir preço (ADMIN ou GERENTE da unidade)')
def move_stock(id: int, data: Estoque, user: Manager):
    s.unit_access(user, id)
    with transaction() as db:
        s.get(db, 'units', id)
        s.get(db, 'products', data.produtoId)
        exists = db.execute('SELECT 1 FROM stocks WHERE unidade_id=? AND produto_id=?', (id, data.produtoId)).fetchone()
        if not exists:
            require(data.precoCentavos is not None, 'PRECO_OBRIGATORIO', 'Primeiro movimento exige preço.', 422)
            db.execute('INSERT INTO stocks VALUES (?,?,0,?)', (id, data.produtoId, data.precoCentavos))
        elif data.precoCentavos is not None:
            db.execute('UPDATE stocks SET preco_centavos=? WHERE unidade_id=? AND produto_id=?', (data.precoCentavos, id, data.produtoId))
        s.stock_move(db, id, data.produtoId, data.quantidade, data.motivo, user['id'])
        s.audit(db, user['id'], 'ESTOQUE_MOVIMENTADO', f'{id}/{data.produtoId}')
        return dict(db.execute('SELECT produto_id AS produtoId,quantidade,preco_centavos AS precoCentavos FROM stocks WHERE unidade_id=? AND produto_id=?', (id, data.produtoId)).fetchone())

@app.post('/pedidos', status_code=201, response_model=PedidoResponse, tags=['Pedidos'], summary='Criar pedido (CLIENTE próprio ou equipe de atendimento; Idempotency-Key obrigatório)')
def create_order(data: Pedido, user: Buyer, key: Key):
    return s.create_order(user, data, key)

@app.get('/pedidos', response_model=PageResponse[PedidoResponse], tags=['Pedidos'], summary='Listar pedidos autorizados, filtrados por canal e status (JWT)')
def orders(user: User, page: Page = 1, limit: Limit = 20, canalPedido: Canal | None = None, status: Status | None = None):
    conditions, params = [], []
    if user['perfil'] == 'CLIENTE':
        conditions.append('cliente_id=?'); params.append(user['id'])
    elif user['perfil'] != 'ADMIN':
        conditions.append('unidade_id=?'); params.append(user['unidade_id'])
    if canalPedido:
        conditions.append('canal_pedido=?'); params.append(canalPedido.value)
    if status:
        conditions.append('status=?'); params.append(status.value)
    where = ' WHERE ' + ' AND '.join(conditions) if conditions else ''
    with transaction() as db:
        rows = db.execute('SELECT id FROM orders' + where + ' ORDER BY id DESC LIMIT ? OFFSET ?', (*params, limit, (page-1)*limit)).fetchall()
        s.audit(db, user['id'], 'PEDIDOS_CONSULTADOS', user['id'])
        return page_result([s.order_json(db, r['id']) for r in rows], page, limit)

@app.get('/pedidos/{id}', response_model=PedidoResponse, tags=['Pedidos'], summary='Consultar pedido autorizado (JWT)')
def order(id: int, user: User):
    with transaction() as db:
        s.order_access(user, s.get(db, 'orders', id))
        s.audit(db, user['id'], 'PEDIDO_CONSULTADO', id)
        return s.order_json(db, id)

@app.patch('/pedidos/{id}/status', response_model=PedidoResponse, tags=['Pedidos'], summary='Avançar status ou cancelar conforme perfil e unidade')
def status_order(id: int, data: MudancaStatus, user: User):
    return s.update_status(user, id, data.status.value)

@app.post('/pedidos/{id}/pagamentos', status_code=201, response_model=PagamentoResponse, tags=['Pagamentos'], summary='Solicitar pagamento mock (CLIENTE próprio ou atendimento; Idempotency-Key obrigatório)')
def pay(id: int, data: Pagamento, user: Buyer, key: Key):
    return s.pay(user, id, data.cenario, key)

@app.get('/pedidos/{id}/pagamentos', response_model=PageResponse[PagamentoResponse], tags=['Pagamentos'], summary='Consultar pagamentos autorizados (JWT)')
def payments(id: int, user: User, page: Page = 1, limit: Limit = 20):
    with transaction() as db:
        s.order_access(user, s.get(db, 'orders', id))
        s.audit(db, user['id'], 'PAGAMENTOS_CONSULTADOS', id)
        rows = db.execute('SELECT * FROM payments WHERE pedido_id=? ORDER BY id LIMIT ? OFFSET ?', (id, limit, (page-1)*limit))
        return page_result([s.payment_json(r) for r in rows], page, limit)

@app.put('/fidelidade/consentimento', response_model=ConsentimentoResponse, tags=['Fidelidade'], summary='Registrar ou revogar consentimento próprio (CLIENTE)')
def consent(data: Consentimento, user: Client):
    return s.consent(user, data.consentimento)

@app.get('/fidelidade', response_model=FidelidadeResponse, tags=['Fidelidade'], summary='Consultar saldo e histórico próprio (CLIENTE)')
def loyalty(user: Client, page: Page = 1, limit: Limit = 20):
    with transaction() as db:
        row = s.get(db, 'users', user['id'])
        s.audit(db, user['id'], 'FIDELIDADE_CONSULTADA', user['id'])
        history = db.execute('SELECT pedido_id AS pedidoId,quantidade,motivo,created_at AS createdAt FROM loyalty_movements WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?', (user['id'], limit, (page-1)*limit))
        return {'saldo': row['points'], 'consentimento': bool(row['consent']), 'consentimentoEm': row['consent_at'],
                'versao': row['consent_version'], **page_result([dict(r) for r in history], page, limit)}

@app.get('/auditoria', response_model=PageResponse[AuditoriaResponse], tags=['Auditoria'], summary='Consultar ações sensíveis (ADMIN)')
def audit(user: Admin, page: Page = 1, limit: Limit = 20):
    with transaction() as db:
        s.audit(db, user['id'], 'AUDITORIA_CONSULTADA', user['id'])
        return page_result(resource_list(db, 'audit', page, limit), page, limit)


# Acrescenta exemplos sem substituir os contratos gerados pelas rotas reais.
_original_openapi = app.openapi
def documented_openapi():
    from app.api.examples import EXAMPLES
    document = _original_openapi()
    schemas = document['components']['schemas']
    for name, example in EXAMPLES.items():
        if name in schemas:
            schemas[name]['example'] = example
    def example(shape):
        if '$ref' in shape:
            name = shape['$ref'].split('/')[-1]
            return EXAMPLES[name] if name in EXAMPLES else example(schemas[name])
        if 'example' in shape:
            return shape['example']
        if 'enum' in shape:
            return shape['enum'][0]
        if 'default' in shape:
            return shape['default']
        if 'anyOf' in shape:
            return example(shape['anyOf'][0])
        if shape.get('type') == 'object':
            return {k: example(v) for k,v in shape.get('properties', {}).items()}
        if shape.get('type') == 'array':
            return [example(shape['items'])]
        if shape.get('type') == 'integer':
            return 1
        if shape.get('type') == 'boolean':
            return True
        return 'texto'
    for path in document['paths'].values():
        for operation in path.values():
            for response in operation.get('responses', {}).values():
                content = response.get('content', {}).get('application/json')
                if content and content.get('schema'):
                    content['example'] = example(content['schema'])
    app.openapi_schema = document
    return document
app.openapi = documented_openapi
