"""Gateway simulado, sem tráfego externo nem dados de cartão."""
def request_payment(order_id, amount, scenario):
    statuses = {'APROVAR': 'APROVADO', 'NEGAR': 'NEGADO', 'INDISPONIVEL': 'ERRO'}
    return {'gateway': 'MOCK', 'referencia': f'mock-{order_id}', 'status': statuses[scenario],
            'valorCentavos': amount, 'mensagem': {'APROVAR': 'Pagamento aprovado.',
            'NEGAR': 'Pagamento negado.', 'INDISPONIVEL': 'Gateway indisponível; tente novamente.'}[scenario]}
