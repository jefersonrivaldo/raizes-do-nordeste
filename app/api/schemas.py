from typing import Annotated, Literal
from pydantic import BaseModel, Field, ConfigDict, field_validator
from app.domain.rules import Canal, Perfil, Status

Positive = Annotated[int, Field(gt=0, strict=True)]
Name = Annotated[str, Field(min_length=2, max_length=120)]
class Contract(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)

class Cadastro(Contract):
    nome: Name
    email: Annotated[str, Field(min_length=5, max_length=254, pattern=r'^[^\s@]+@[^\s@]+\.[^\s@]+$')]
    senha: Annotated[str, Field(min_length=10, max_length=128)]
    @field_validator('senha')
    @classmethod
    def strength(cls, value):
        if not any(c.isalpha() for c in value) or not any(c.isdigit() for c in value):
            raise ValueError('Senha precisa conter letras e números.')
        return value

class Login(Contract):
    email: Annotated[str, Field(min_length=5, max_length=254)]
    senha: Annotated[str, Field(min_length=1, max_length=128)]

class Unidade(Contract):
    nome: Name
    endereco: Annotated[str, Field(min_length=5, max_length=250)]
    ativo: bool = True

class Produto(Contract):
    nome: Name
    descricao: Annotated[str, Field(max_length=500)] = ''
    ativo: bool = True

class Vinculo(Contract):
    perfil: Perfil
    unidadeId: Positive | None = None

class Estoque(Contract):
    produtoId: Positive
    quantidade: Annotated[int, Field(strict=True, ge=-100000, le=100000)]
    precoCentavos: Annotated[int, Field(strict=True, gt=0, le=10000000)] | None = None
    motivo: Annotated[str, Field(min_length=3, max_length=120)]

class Item(Contract):
    produtoId: Positive
    quantidade: Annotated[int, Field(strict=True, gt=0, le=1000)]

class Pedido(Contract):
    unidadeId: Positive
    canalPedido: Canal
    clienteId: Positive | None = None
    itens: Annotated[list[Item], Field(min_length=1, max_length=50)]
    formaPagamento: Literal['MOCK'] = 'MOCK'
    cupom: Annotated[str, Field(max_length=30)] | None = None
    pontosResgate: Annotated[int, Field(strict=True, ge=0, le=1000000)] = 0
    model_config = ConfigDict(extra='forbid', json_schema_extra={'example': {'unidadeId': 1,
        'canalPedido': 'APP', 'itens': [{'produtoId': 1, 'quantidade': 2}], 'formaPagamento': 'MOCK'}})

class Pagamento(Contract):
    cenario: Literal['APROVAR', 'NEGAR', 'INDISPONIVEL'] = 'APROVAR'

class MudancaStatus(Contract):
    status: Status

class Consentimento(Contract):
    consentimento: bool

class ErrorDetail(BaseModel):
    field: str
    issue: str
class ErrorResponse(BaseModel):
    error: str
    message: str
    details: list[ErrorDetail]
    timestamp: str
    path: str
    requestId: str

class UsuarioResponse(BaseModel):
    id: int
    nome: str
    perfil: Perfil
    unidadeId: int | None
class LoginResponse(BaseModel):
    accessToken: str
    tokenType: str
    expiresIn: int
    user: UsuarioResponse
class ItemResponse(BaseModel):
    produtoId: int
    nome: str
    quantidade: int
    precoUnitarioCentavos: int
class PedidoResponse(BaseModel):
    id: int
    clienteId: int
    unidadeId: int
    canalPedido: Canal
    status: Status
    subtotalCentavos: int
    descontoCentavos: int
    totalCentavos: int
    pontosResgate: int
    createdAt: str
    itens: list[ItemResponse]
class PagamentoResponse(BaseModel):
    id: int
    pedidoId: int
    status: Literal['APROVADO','NEGADO','ERRO','ESTORNADO']
    valorCentavos: int
    payload: dict

from typing import Generic, TypeVar
T = TypeVar('T')
class PageResponse(BaseModel, Generic[T]):
    items: list[T]
    page: int
    limit: int
class UnidadeResponse(BaseModel):
    id: int
    nome: str
    endereco: str
    active: bool
class ProdutoResponse(BaseModel):
    id: int
    nome: str
    descricao: str
    active: bool
class EstoqueResponse(BaseModel):
    produtoId: int
    quantidade: int
    precoCentavos: int
class CardapioResponse(BaseModel):
    produtoId: int
    nome: str
    descricao: str
    precoCentavos: int
    disponivel: int
class MovimentoFidelidadeResponse(BaseModel):
    pedidoId: int | None
    quantidade: int
    motivo: str
    createdAt: str
class FidelidadeResponse(PageResponse[MovimentoFidelidadeResponse]):
    saldo: int
    consentimento: bool
    consentimentoEm: str | None
    versao: str | None
class ConsentimentoResponse(BaseModel):
    consentimento: bool
    versao: str
class AuditoriaResponse(BaseModel):
    id: int
    actor_id: int
    acao: str
    recurso: str
    created_at: str
class HealthResponse(BaseModel):
    status: Literal['ok']
    database: Literal['ok']
