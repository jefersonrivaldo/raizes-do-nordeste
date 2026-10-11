# Contratos da API

Documento gerado a partir das rotas e schemas reais. Exemplos são ilustrativos, não registros de produção. HTTP 201 no pagamento significa tentativa persistida; examine status APROVADO/NEGADO/ERRO no corpo. Centavos são inteiros. Campos extras são rejeitados.

Nas listagens, page inicia em 1 e limit varia de 1 a 100 (padrão 20). Idempotency-Key tem 8 a 100 caracteres alfanuméricos, ponto, hífen ou sublinhado. Reenvios com mesmo corpo/chave devolvem o recurso no estado atual, sem repetir efeitos. Equipe de atendimento informa clienteId na criação.

## GET /health

Verificar API e banco

Autenticação: Público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "status": "ok",
  "database": "ok"
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /usuarios

Cadastrar cliente público

Autenticação: Público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

```json
{
  "nome": "Maria de estudo",
  "email": "maria@example.com",
  "senha": "SenhaEstudo123!"
}
```

Response HTTP 201:

```json
{
  "id": 2,
  "nome": "Maria de estudo",
  "perfil": "CLIENTE",
  "unidadeId": null
}
```

Status documentados: 201, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /auth/login

Autenticar e emitir JWT

Autenticação: Público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

```json
{
  "email": "maria@example.com",
  "senha": "SenhaEstudo123!"
}
```

Response HTTP 200:

```json
{
  "accessToken": "<JWT emitido no login>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 2,
    "nome": "Maria de estudo",
    "perfil": "CLIENTE",
    "unidadeId": null
  }
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /usuarios/me

Consultar perfil próprio (JWT)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "id": 2,
  "nome": "Maria de estudo",
  "perfil": "CLIENTE",
  "unidadeId": null
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## DELETE /usuarios/me

Anonimizar cliente (CLIENTE, sem pedidos em aberto)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

Sem corpo.

Response HTTP 204: sem corpo.

Status documentados: 204, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## PUT /usuarios/{id}/perfil

Definir perfil e unidade (ADMIN)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |

Request:

```json
{
  "perfil": "COZINHA",
  "unidadeId": 1
}
```

Response HTTP 200:

```json
{
  "id": 2,
  "nome": "Maria de estudo",
  "perfil": "CLIENTE",
  "unidadeId": null
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /unidades

Listar unidades (público)

Autenticação: Público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "id": 1,
      "nome": "Recife Centro",
      "endereco": "Rua comercial, 100",
      "active": true
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /unidades

Cadastrar unidade (ADMIN)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

```json
{
  "nome": "Recife Centro",
  "endereco": "Rua comercial, 100",
  "ativo": true
}
```

Response HTTP 201:

```json
{
  "id": 1,
  "nome": "Recife Centro",
  "endereco": "Rua comercial, 100",
  "active": true
}
```

Status documentados: 201, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## PUT /unidades/{id}

Atualizar unidade (ADMIN ou GERENTE da unidade)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |

Request:

```json
{
  "nome": "Recife Centro",
  "endereco": "Rua comercial, 100",
  "ativo": true
}
```

Response HTTP 200:

```json
{
  "id": 1,
  "nome": "Recife Centro",
  "endereco": "Rua comercial, 100",
  "active": true
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /produtos

Listar catálogo (público)

Autenticação: Público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "id": 1,
      "nome": "Baião de dois",
      "descricao": "Porção individual",
      "active": true
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /produtos

Cadastrar produto no catálogo global (ADMIN)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

```json
{
  "nome": "Baião de dois",
  "descricao": "Porção individual",
  "ativo": true
}
```

Response HTTP 201:

```json
{
  "id": 1,
  "nome": "Baião de dois",
  "descricao": "Porção individual",
  "active": true
}
```

Status documentados: 201, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## PUT /produtos/{id}

Atualizar produto global (ADMIN)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |

Request:

```json
{
  "nome": "Baião de dois",
  "descricao": "Porção individual",
  "ativo": true
}
```

Response HTTP 200:

```json
{
  "id": 1,
  "nome": "Baião de dois",
  "descricao": "Porção individual",
  "active": true
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /unidades/{id}/cardapio

Consultar preços e disponibilidade por unidade (público)

Autenticação: Público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "produtoId": 1,
      "nome": "Baião de dois",
      "descricao": "Porção individual",
      "precoCentavos": 2500,
      "disponivel": 100
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /unidades/{id}/estoque

Consultar estoque próprio (ADMIN ou GERENTE)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "produtoId": 1,
      "quantidade": 100,
      "precoCentavos": 2500
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /unidades/{id}/estoque/movimentos

Movimentar estoque e definir preço (ADMIN ou GERENTE da unidade)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |

Request:

```json
{
  "produtoId": 1,
  "quantidade": 10,
  "precoCentavos": 2500,
  "motivo": "Reposição"
}
```

Response HTTP 201:

```json
{
  "produtoId": 1,
  "quantidade": 100,
  "precoCentavos": 2500
}
```

Status documentados: 201, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /pedidos

Criar pedido (CLIENTE próprio ou equipe de atendimento; Idempotency-Key obrigatório)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| Idempotency-Key | header | sim | `{"type": "string", "minLength": 8, "maxLength": 100, "pattern": "^[A-Za-z0-9_.-]+$", "title": "Idempotency-Key"}` |

Request:

```json
{
  "canalPedido": "APP",
  "formaPagamento": "MOCK",
  "itens": [
    {
      "produtoId": 1,
      "quantidade": 2
    }
  ],
  "unidadeId": 1
}
```

Response HTTP 201:

```json
{
  "id": 1,
  "clienteId": 2,
  "unidadeId": 1,
  "canalPedido": "TOTEM",
  "status": "AGUARDANDO_PAGAMENTO",
  "subtotalCentavos": 5000,
  "descontoCentavos": 0,
  "totalCentavos": 5000,
  "pontosResgate": 0,
  "createdAt": "2026-10-11T15:00:00+00:00",
  "itens": [
    {
      "produtoId": 1,
      "nome": "Baião de dois",
      "quantidade": 2,
      "precoUnitarioCentavos": 2500
    }
  ]
}
```

Status documentados: 201, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /pedidos

Listar pedidos autorizados, filtrados por canal e status (JWT)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |
| canalPedido | query | não | `{"anyOf": [{"$ref": "#/components/schemas/Canal"}, {"type": "null"}], "title": "Canalpedido"}` |
| status | query | não | `{"anyOf": [{"$ref": "#/components/schemas/Status"}, {"type": "null"}], "title": "Status"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "id": 1,
      "clienteId": 2,
      "unidadeId": 1,
      "canalPedido": "TOTEM",
      "status": "AGUARDANDO_PAGAMENTO",
      "subtotalCentavos": 5000,
      "descontoCentavos": 0,
      "totalCentavos": 5000,
      "pontosResgate": 0,
      "createdAt": "2026-10-11T15:00:00+00:00",
      "itens": [
        {
          "produtoId": 1,
          "nome": "Baião de dois",
          "quantidade": 2,
          "precoUnitarioCentavos": 2500
        }
      ]
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /pedidos/{id}

Consultar pedido autorizado (JWT)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "id": 1,
  "clienteId": 2,
  "unidadeId": 1,
  "canalPedido": "TOTEM",
  "status": "AGUARDANDO_PAGAMENTO",
  "subtotalCentavos": 5000,
  "descontoCentavos": 0,
  "totalCentavos": 5000,
  "pontosResgate": 0,
  "createdAt": "2026-10-11T15:00:00+00:00",
  "itens": [
    {
      "produtoId": 1,
      "nome": "Baião de dois",
      "quantidade": 2,
      "precoUnitarioCentavos": 2500
    }
  ]
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## PATCH /pedidos/{id}/status

Avançar status ou cancelar conforme perfil e unidade

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |

Request:

```json
{
  "status": "PRONTO"
}
```

Response HTTP 200:

```json
{
  "id": 1,
  "clienteId": 2,
  "unidadeId": 1,
  "canalPedido": "TOTEM",
  "status": "AGUARDANDO_PAGAMENTO",
  "subtotalCentavos": 5000,
  "descontoCentavos": 0,
  "totalCentavos": 5000,
  "pontosResgate": 0,
  "createdAt": "2026-10-11T15:00:00+00:00",
  "itens": [
    {
      "produtoId": 1,
      "nome": "Baião de dois",
      "quantidade": 2,
      "precoUnitarioCentavos": 2500
    }
  ]
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## POST /pedidos/{id}/pagamentos

Solicitar pagamento mock (CLIENTE próprio ou atendimento; Idempotency-Key obrigatório)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |
| Idempotency-Key | header | sim | `{"type": "string", "minLength": 8, "maxLength": 100, "pattern": "^[A-Za-z0-9_.-]+$", "title": "Idempotency-Key"}` |

Request:

```json
{
  "cenario": "APROVAR"
}
```

Response HTTP 201:

```json
{
  "id": 1,
  "pedidoId": 1,
  "status": "APROVADO",
  "valorCentavos": 5000,
  "payload": {
    "gateway": "MOCK",
    "referencia": "mock-1",
    "status": "APROVADO",
    "valorCentavos": 5000,
    "mensagem": "Pagamento aprovado."
  }
}
```

Status documentados: 201, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /pedidos/{id}/pagamentos

Consultar pagamentos autorizados (JWT)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | sim | `{"type": "integer", "title": "Id"}` |
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "id": 1,
      "pedidoId": 1,
      "status": "APROVADO",
      "valorCentavos": 5000,
      "payload": {
        "gateway": "MOCK",
        "referencia": "mock-1",
        "status": "APROVADO",
        "valorCentavos": 5000,
        "mensagem": "Pagamento aprovado."
      }
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## PUT /fidelidade/consentimento

Registrar ou revogar consentimento próprio (CLIENTE)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| — | — | — | Sem parâmetros |

Request:

```json
{
  "consentimento": true
}
```

Response HTTP 200:

```json
{
  "consentimento": true,
  "versao": "1.0"
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /fidelidade

Consultar saldo e histórico próprio (CLIENTE)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "saldo": 50,
  "consentimento": true,
  "consentimentoEm": "2026-10-11T15:00:00+00:00",
  "versao": "1.0",
  "items": [
    {
      "pedidoId": 1,
      "quantidade": 50,
      "motivo": "COMPRA",
      "createdAt": "2026-10-11T15:00:00+00:00"
    }
  ],
  "page": 1,
  "limit": 20
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## GET /auditoria

Consultar ações sensíveis (ADMIN)

Autenticação: JWT Bearer; permissões conforme finalidade acima.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | não | `{"type": "integer", "minimum": 1, "default": 1, "title": "Page"}` |
| limit | query | não | `{"type": "integer", "maximum": 100, "minimum": 1, "default": 20, "title": "Limit"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items": [
    {
      "id": 1,
      "actor_id": 2,
      "acao": "PEDIDO_CRIADO",
      "recurso": "1",
      "created_at": "2026-10-11T15:00:00+00:00"
    }
  ],
  "page": 1,
  "limit": 1
}
```

Status documentados: 200, 401, 403, 404, 409, 422, 503.

Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, 422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.

## Padrão de erro

```json
{
  "error": "ESTOQUE_INSUFICIENTE",
  "message": "Estoque insuficiente na unidade.",
  "details": [],
  "timestamp": "2026-10-11T15:00:00+00:00",
  "path": "/pedidos",
  "requestId": "00000000-0000-4000-8000-000000000001"
}
```

Para validação, details identifica o campo e o problema, sem repetir dados enviados. Exemplo: canalPedido omitido gera HTTP 422 e error=VALIDACAO.
