"""Regras puras de domínio; valores monetários são inteiros em centavos."""
from enum import Enum

class Canal(str, Enum):
    APP = 'APP'
    TOTEM = 'TOTEM'
    BALCAO = 'BALCAO'
    PICKUP = 'PICKUP'
    WEB = 'WEB'

class Perfil(str, Enum):
    CLIENTE = 'CLIENTE'
    ATENDENTE = 'ATENDENTE'
    COZINHA = 'COZINHA'
    GERENTE = 'GERENTE'
    ADMIN = 'ADMIN'

class Status(str, Enum):
    AGUARDANDO_PAGAMENTO = 'AGUARDANDO_PAGAMENTO'
    EM_PREPARO = 'EM_PREPARO'
    PRONTO = 'PRONTO'
    ENTREGUE = 'ENTREGUE'
    CANCELADO = 'CANCELADO'

class BusinessError(Exception):
    def __init__(self, code, message, status=409, details=None):
        self.code, self.message, self.status = code, message, status
        self.details = details or []

def require(condition, code, message, status=409):
    if not condition:
        raise BusinessError(code, message, status)

def transition(current, target):
    allowed = {'EM_PREPARO': {'PRONTO', 'CANCELADO'}, 'PRONTO': {'ENTREGUE', 'CANCELADO'},
               'AGUARDANDO_PAGAMENTO': {'CANCELADO'}}
    require(target in allowed.get(current, set()), 'TRANSICAO_INVALIDA', 'Transição de status inválida.')

def totals(subtotal, coupon, points):
    if coupon:
        require(coupon == 'NORDESTE10' and subtotal >= 5000, 'CUPOM_INVALIDO',
                'NORDESTE10 exige subtotal de pelo menos 5000 centavos.')
    require(points % 100 == 0, 'RESGATE_INVALIDO', 'Resgate deve ser múltiplo de 100 pontos.')
    discount = subtotal // 10 if coupon else 0
    require(points * 10 <= subtotal - discount, 'RESGATE_INVALIDO', 'Resgate excede o valor do pedido.')
    return subtotal - discount - points * 10, discount + points * 10
