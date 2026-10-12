# Contratos da API Java

Gerado do OpenAPI real do Spring Boot. Exemplos são ilustrativos. Valores em centavos; listagens retornam items/page/limit (limit até 100). Idempotency-Key tem 8–100 caracteres alfanuméricos, ponto, hífen ou sublinhado. Equipe informa clienteId na criação de pedido. Reenvio idempotente retorna estado atual.

## PUT /usuarios/{id}/perfil

Atribuir perfil e unidade (ADMIN)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |

Request:

```json
{
  "perfil" : "CLIENTE",
  "unidadeId" : null
}
```

Response HTTP 200:

```json
{
  "id" : 2,
  "nome" : "Maria de estudo",
  "perfil" : "CLIENTE",
  "unidadeId" : null
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## PUT /unidades/{id}

Atualizar unidade (ADMIN ou GERENTE da unidade)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |

Request:

```json
{
  "nome" : "texto",
  "endereco" : "texto",
  "ativo" : true
}
```

Response HTTP 200:

```json
{
  "id" : 1,
  "nome" : "texto",
  "endereco" : "texto",
  "active" : true
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## PUT /produtos/{id}

Atualizar produto global (ADMIN)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |

Request:

```json
{
  "nome" : "texto",
  "descricao" : "texto",
  "ativo" : true
}
```

Response HTTP 200:

```json
{
  "id" : 1,
  "nome" : "texto",
  "descricao" : "texto",
  "active" : true
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## PUT /fidelidade/consentimento

Registrar ou revogar consentimento próprio (CLIENTE)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

```json
{
  "consentimento" : true
}
```

Response HTTP 200:

```json
{
  "consentimento" : true,
  "versao" : "texto"
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /usuarios

Cadastrar cliente (público; não aceita perfil)



Autenticação: público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

```json
{
  "email" : "maria@example.com",
  "nome" : "Maria de estudo",
  "senha" : "SenhaEstudo123!"
}
```

Response HTTP 201:

```json
{
  "id" : 2,
  "nome" : "Maria de estudo",
  "perfil" : "CLIENTE",
  "unidadeId" : null
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

Limite de tentativas: HTTP 429 com Retry-After indicando os segundos até uma nova tentativa.

## GET /unidades

Listar unidades (público)



Autenticação: público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "id" : 1,
    "nome" : "texto",
    "endereco" : "texto",
    "active" : true
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /unidades

Cadastrar unidade (ADMIN)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

```json
{
  "nome" : "texto",
  "endereco" : "texto",
  "ativo" : true
}
```

Response HTTP 201:

```json
{
  "id" : 1,
  "nome" : "texto",
  "endereco" : "texto",
  "active" : true
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /unidades/{id}/estoque/movimentos

Movimentar estoque e definir preço (ADMIN ou GERENTE da unidade)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |

Request:

```json
{
  "produtoId" : 1,
  "quantidade" : 1,
  "precoCentavos" : 1,
  "motivo" : "texto"
}
```

Response HTTP 201:

```json
{
  "produtoId" : 1,
  "quantidade" : 1,
  "precoCentavos" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /produtos

Listar catálogo (público)



Autenticação: público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "id" : 1,
    "nome" : "texto",
    "descricao" : "texto",
    "active" : true
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /produtos

Cadastrar produto global (ADMIN)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

```json
{
  "nome" : "texto",
  "descricao" : "texto",
  "ativo" : true
}
```

Response HTTP 201:

```json
{
  "id" : 1,
  "nome" : "texto",
  "descricao" : "texto",
  "active" : true
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /pedidos

Consultar pedidos autorizados com filtro por canal/status (JWT)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |
| canalPedido | query | false | `{"type":"string","enum":["APP","TOTEM","BALCAO","PICKUP","WEB"]}` |
| status | query | false | `{"type":"string","enum":["AGUARDANDO_PAGAMENTO","EM_PREPARO","PRONTO","ENTREGUE","CANCELADO"]}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "id" : 1,
    "clienteId" : 1,
    "unidadeId" : 1,
    "canalPedido" : "APP",
    "status" : "AGUARDANDO_PAGAMENTO",
    "subtotalCentavos" : 1,
    "descontoCentavos" : 1,
    "totalCentavos" : 1,
    "pontosResgate" : 1,
    "createdAt" : "texto",
    "itens" : [ {
      "produtoId" : 1,
      "nome" : "texto",
      "quantidade" : 1,
      "precoUnitarioCentavos" : 1
    } ]
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /pedidos

Criar pedido multicanal (cliente próprio ou atendimento na unidade)

Idempotency-Key obrigatório. Equipe informa clienteId. Estoque e pontos são debitados atomicamente; reenvio da mesma chave/corpo devolve o recurso atual.

Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| Idempotency-Key | header | true | `{"type":"string","maxLength":100,"minLength":8,"pattern":"[A-Za-z0-9_.-]+"}` |

Request:

```json
{
  "itens" : [ {
    "produtoId" : 1,
    "quantidade" : 2
  } ],
  "canalPedido" : "TOTEM",
  "unidadeId" : 1,
  "formaPagamento" : "MOCK"
}
```

Response HTTP 201:

```json
{
  "id" : 1,
  "clienteId" : 1,
  "unidadeId" : 1,
  "canalPedido" : "APP",
  "status" : "AGUARDANDO_PAGAMENTO",
  "subtotalCentavos" : 1,
  "descontoCentavos" : 1,
  "totalCentavos" : 1,
  "pontosResgate" : 1,
  "createdAt" : "texto",
  "itens" : [ {
    "produtoId" : 1,
    "nome" : "texto",
    "quantidade" : 1,
    "precoUnitarioCentavos" : 1
  } ]
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /pedidos/{id}/pagamentos

Listar pagamentos próprios ou da unidade autorizada (JWT)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "id" : 1,
    "pedidoId" : 1,
    "status" : "texto",
    "valorCentavos" : 1,
    "payload" : { }
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /pedidos/{id}/pagamentos

Registrar tentativa de pagamento MOCK (cliente próprio ou atendimento autorizado)

HTTP 201 significa tentativa criada. Examine APROVADO/NEGADO/ERRO no corpo; nova tentativa exige nova chave. Apenas aprovação permite EM_PREPARO.

Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |
| Idempotency-Key | header | true | `{"type":"string","maxLength":100,"minLength":8,"pattern":"[A-Za-z0-9_.-]+"}` |

Request:

```json
{
  "cenario" : "APROVAR"
}
```

Response HTTP 201:

```json
{
  "id" : 1,
  "pedidoId" : 1,
  "status" : "texto",
  "valorCentavos" : 1,
  "payload" : { }
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## POST /auth/login

Login público e emissão de JWT



Autenticação: público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

```json
{
  "email" : "maria@example.com",
  "senha" : "SenhaEstudo123!"
}
```

Response HTTP 200:

```json
{
  "accessToken" : "texto",
  "tokenType" : "texto",
  "expiresIn" : 1,
  "user" : {
    "id" : 2,
    "nome" : "Maria de estudo",
    "perfil" : "CLIENTE",
    "unidadeId" : null
  }
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

Limite de tentativas: HTTP 429 com Retry-After indicando os segundos até uma nova tentativa.

## PATCH /pedidos/{id}/status

Avançar status ou cancelar conforme perfil e unidade

Cliente cancela apenas antes de pagar; cozinha apenas marca PRONTO; atendimento avança PRONTO/ENTREGUE ou cancela antes da entrega.

Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |

Request:

```json
{
  "status" : "AGUARDANDO_PAGAMENTO"
}
```

Response HTTP 200:

```json
{
  "id" : 1,
  "clienteId" : 1,
  "unidadeId" : 1,
  "canalPedido" : "APP",
  "status" : "AGUARDANDO_PAGAMENTO",
  "subtotalCentavos" : 1,
  "descontoCentavos" : 1,
  "totalCentavos" : 1,
  "pontosResgate" : 1,
  "createdAt" : "texto",
  "itens" : [ {
    "produtoId" : 1,
    "nome" : "texto",
    "quantidade" : 1,
    "precoUnitarioCentavos" : 1
  } ]
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /usuarios/me

Consultar perfil próprio (JWT)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "id" : 2,
  "nome" : "Maria de estudo",
  "perfil" : "CLIENTE",
  "unidadeId" : null
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## DELETE /usuarios/me

Desidentificar conta própria (CLIENTE, sem pedidos abertos)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

Sem corpo.

Response HTTP 204:

Sem corpo.

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /unidades/{id}/estoque

Consultar estoque (ADMIN ou GERENTE da unidade)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "produtoId" : 1,
    "quantidade" : 1,
    "precoCentavos" : 1
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /unidades/{id}/cardapio

Consultar cardápio por unidade (público)



Autenticação: público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "produtoId" : 1,
    "nome" : "texto",
    "descricao" : "texto",
    "precoCentavos" : 1,
    "disponivel" : 1
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /pedidos/{id}

Consultar pedido próprio ou da unidade autorizada (JWT)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| id | path | true | `{"type":"integer","format":"int64"}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "id" : 1,
  "clienteId" : 1,
  "unidadeId" : 1,
  "canalPedido" : "APP",
  "status" : "AGUARDANDO_PAGAMENTO",
  "subtotalCentavos" : 1,
  "descontoCentavos" : 1,
  "totalCentavos" : 1,
  "pontosResgate" : 1,
  "createdAt" : "texto",
  "itens" : [ {
    "produtoId" : 1,
    "nome" : "texto",
    "quantidade" : 1,
    "precoUnitarioCentavos" : 1
  } ]
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /health

Verificar API e banco (público)



Autenticação: público.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|

Request:

Sem corpo.

Response HTTP 200:

```json
{ }
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /fidelidade

Consultar saldo e histórico próprio (CLIENTE)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "saldo" : 1,
  "consentimento" : true,
  "consentimentoEm" : "texto",
  "versao" : "texto",
  "items" : [ {
    "pedidoId" : 1,
    "quantidade" : 1,
    "motivo" : "texto",
    "createdAt" : "texto"
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## GET /auditoria

Consultar auditoria (ADMIN)



Autenticação: JWT Bearer; perfil/unidade conforme resumo.

Parâmetros:

| Nome | Local | Obrigatório | Contrato |
|---|---|---|---|
| page | query | false | `{"type":"integer","format":"int32","default":1,"maximum":1000000,"minimum":1}` |
| limit | query | false | `{"type":"integer","format":"int32","default":20,"maximum":100,"minimum":1}` |

Request:

Sem corpo.

Response HTTP 200:

```json
{
  "items" : [ {
    "id" : 1,
    "actor_id" : 1,
    "acao" : "texto",
    "recurso" : "texto",
    "created_at" : "texto"
  } ],
  "page" : 1,
  "limit" : 1
}
```

Erros: 401 autenticação, 403 autorização, 404 inexistente, 405 método HTTP, 406 representação, 409 regra/integridade, 415 tipo de conteúdo, 422 validação, 503 banco ocupado; JSON ErrorResponse uniforme. Nem todos os erros se aplicam a toda rota.

## Padrão de erro

```json
{"error":"ESTOQUE_INSUFICIENTE","message":"Estoque insuficiente na unidade.","details":[],"timestamp":"2026-10-11T15:00:00Z","path":"/pedidos","requestId":"uuid"}
```

HTTP 201 em pagamento significa tentativa persistida; examine status APROVADO/NEGADO/ERRO. O campo status representa o estado atual e pode mudar para ESTORNADO após cancelamento. payload preserva a resposta original do gateway mock, mesmo após o estorno. Erros de validação omitem os valores enviados.
