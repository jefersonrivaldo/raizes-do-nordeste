# Sequência do fluxo crítico

```mermaid
sequenceDiagram
    actor Cliente
    participant API
    participant Aplicacao
    participant DB as SQLite
    participant Gateway as Gateway mock
    actor Cozinha
    actor Atendimento
    Cliente->>API: POST /pedidos + JWT + canalPedido + chave
    API->>Aplicacao: Contrato validado e ator autorizado
    Aplicacao->>DB: BEGIN IMMEDIATE; verificar estoque/pontos
    alt disponível
        Aplicacao->>DB: Pedido + itens + baixa + auditoria + idempotência
        Aplicacao->>DB: COMMIT
        API-->>Cliente: 201 AGUARDANDO_PAGAMENTO
        Cliente->>API: POST /pedidos/id/pagamentos + chave
        API->>Aplicacao: Solicitar pagamento
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
    else insuficiente
        Aplicacao->>DB: ROLLBACK
        API-->>Cliente: 409 erro padronizado
    end
```
