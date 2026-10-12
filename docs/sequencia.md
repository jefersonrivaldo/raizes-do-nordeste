# Sequência do fluxo crítico

```mermaid
sequenceDiagram
    actor Cliente
    participant API
    participant Aplicacao
    participant DB as H2
    participant Gateway as Gateway mock
    participant Expiracao as OrderExpiration
    actor Cozinha
    actor Atendimento
    Cliente->>API: POST /pedidos + JWT + canalPedido + chave
    API->>Aplicacao: Contrato validado e ator autorizado
    Aplicacao->>DB: Transação Spring JDBC + FOR UPDATE; verificar estoque/pontos
    alt disponível
        Aplicacao->>DB: Pedido + itens + baixa + auditoria + idempotência
        Aplicacao->>DB: COMMIT
        API-->>Cliente: 201 AGUARDANDO_PAGAMENTO
        Cliente->>API: POST /pedidos/id/pagamentos + chave
        API->>Aplicacao: Solicitar pagamento
        Aplicacao->>DB: Bloquear pedido e verificar vencimento da reserva
        alt reserva vencida sem pagamento
            Aplicacao->>DB: CANCELADO; devolver estoque uma vez e pontos com consentimento ativo
            Aplicacao->>DB: Auditoria e COMMIT
            API-->>Cliente: 409 PEDIDO_EXPIRADO
        else reserva dentro do prazo
            Aplicacao->>Gateway: Pedido e total em centavos
            Gateway-->>Aplicacao: APROVADO / NEGADO / ERRO + payload
            Aplicacao->>DB: Persistir tentativa e auditoria
            alt aprovado
                Aplicacao->>DB: Status EM_PREPARO
                Cozinha->>API: PATCH status PRONTO
                API->>DB: Validar perfil/unidade/transição; persistir
                Atendimento->>API: PATCH status ENTREGUE
                API->>DB: Status e crédito de pontos com consentimento
            else negado ou indisponível
                API-->>Cliente: 201 tentativa registrada, pedido continua aguardando
            end
        end
    else insuficiente
        Aplicacao->>DB: ROLLBACK
        API-->>Cliente: 409 erro padronizado
    end
    opt varredura automática de reservas não pagas
        Expiracao->>Aplicacao: Localizar e cancelar reservas vencidas
        Aplicacao->>DB: Transação por pedido; validar vencimento e devolver estoque uma vez
        Aplicacao->>DB: Restituir pontos com consentimento ativo; auditoria e COMMIT
    end
```

O pagamento verifica o prazo antes de chamar o Gateway mock. A varredura também pode cancelar uma reserva sem que o cliente envie outra requisição. Pedidos pagos não expiram. O [diagrama de atividade complementar](diagramas/sequencia.svg), apresentado no Apêndice H do relatório, representa os mesmos caminhos de aprovação, falha e expiração.
