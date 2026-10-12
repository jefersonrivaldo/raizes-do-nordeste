# Plano de testes executável

Executar no Postman as sete pastas em ordem, mantendo T01–T23; a mesma coleção é validada pelo teste Java CollectionContractTest. Executar na ordem indicada com banco criado pelo seed. Cada cenário tem asserções na coleção. CLIENTE é criado em cada execução; adminPassword é configurado localmente e não deve ser exportado. Unidade 1 e produto 1 vêm do seed.

| ID | Requisição e entrada | Pré-condição | Saída esperada |
|---|---|---|---|
| T01 Cadastro de cliente | `POST /usuarios`; `{"nome": "Cliente de teste", "email": "{{clientEmail}}", "senha": "{{clientPassword}}"}` | API iniciada; email novo gerado automaticamente | HTTP 201; asserções na requisição homônima |
| T02 Login cliente | `POST /auth/login`; `{"email": "{{clientEmail}}", "senha": "{{clientPassword}}"}` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T03 Login administrador | `POST /auth/login`; `{"email": "admin@raizes.local", "senha": "{{adminPassword}}"}` | Seed aplicado; variável adminPassword informada localmente | HTTP 200; asserções na requisição homônima |
| T04 Cardápio por unidade | `GET /unidades/1/cardapio`; `Sem corpo` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T05 Consentimento fidelidade | `PUT /fidelidade/consentimento`; `{"consentimento": true}` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T06 Criar pedido multicanal | `POST /pedidos`; `{"unidadeId": 1, "canalPedido": "TOTEM", "itens": [{"produtoId": 1, "quantidade": 2}], "formaPagamento": "MOCK"}` | Fluxo anterior concluído | HTTP 201; asserções na requisição homônima |
| T07 Idempotência pedido | `POST /pedidos`; `{"unidadeId": 1, "canalPedido": "TOTEM", "itens": [{"produtoId": 1, "quantidade": 2}], "formaPagamento": "MOCK"}` | Fluxo anterior concluído | HTTP 201; asserções na requisição homônima |
| T08 Pagamento negado | `POST /pedidos/{{pedidoId}}/pagamentos`; `{"cenario": "NEGAR"}` | Fluxo anterior concluído | HTTP 201; asserções na requisição homônima |
| T09 Pagamento aprovado em nova tentativa | `POST /pedidos/{{pedidoId}}/pagamentos`; `{"cenario": "APROVAR"}` | Fluxo anterior concluído | HTTP 201; asserções na requisição homônima |
| T10 Cozinha concluiu preparo | `PATCH /pedidos/{{pedidoId}}/status`; `{"status": "PRONTO"}` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T11 Entrega e fidelização | `PATCH /pedidos/{{pedidoId}}/status`; `{"status": "ENTREGUE"}` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T12 Saldo fidelidade | `GET /fidelidade`; `Sem corpo` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T13 Filtro canalPedido | `GET /pedidos?canalPedido=TOTEM&status=ENTREGUE&page=1&limit=20`; `Sem corpo` | Fluxo anterior concluído | HTTP 200; asserções na requisição homônima |
| T14 Sem autenticação | `GET /pedidos`; `Sem corpo` | Omitir Authorization | HTTP 401; asserções na requisição homônima |
| T15 Perfil sem permissão | `POST /unidades`; `{"nome": "Unidade teste", "endereco": "Rua teste, 123"}` | Fluxo anterior concluído | HTTP 403; asserções na requisição homônima |
| T16 Canal obrigatório ausente | `POST /pedidos`; `{"unidadeId": 1, "itens": [{"produtoId": 1, "quantidade": 2}], "formaPagamento": "MOCK"}` | Fluxo anterior concluído | HTTP 422; asserções na requisição homônima |
| T17 Canal inválido | `POST /pedidos`; `{"unidadeId": 1, "canalPedido": "OUTRO", "itens": [{"produtoId": 1, "quantidade": 2}], "formaPagamento": "MOCK"}` | Fluxo anterior concluído | HTTP 422; asserções na requisição homônima |
| T18 Estoque insuficiente | `POST /pedidos`; `{"unidadeId": 1, "canalPedido": "TOTEM", "itens": [{"produtoId": 1, "quantidade": 1000}], "formaPagamento": "MOCK"}` | Seed com saldo de 100; não adicionar estoque antes deste teste | HTTP 409; asserções na requisição homônima |
| T19 Pedido inexistente | `GET /pedidos/999999999`; `Sem corpo` | Fluxo anterior concluído | HTTP 404; asserções na requisição homônima |
| T20 Transição inválida | `PATCH /pedidos/{{pedidoId}}/status`; `{"status": "PRONTO"}` | Fluxo anterior concluído | HTTP 409; asserções na requisição homônima |

| T21 Unidade inexistente | `POST /pedidos`; unidadeId = 999999999; demais campos válidos | CLIENTE autenticado; chave de idempotência nova | HTTP 404; envelope de erro padronizado |
| T22 Produto inexistente | `POST /pedidos`; produtoId = 999999999; demais campos válidos | CLIENTE autenticado; chave de idempotência nova | HTTP 404; envelope de erro padronizado |
| T23 Auditoria de criação do pedido | `GET /auditoria?page=1&limit=100` | ADMIN autenticado; pedido criado anteriormente | HTTP 200; items contém evento PEDIDO_CRIADO |

## Critérios de aceitação e evidências por cenário

A evidência de cada linha é a requisição de mesmo ID e nome em `docs/postman_collection.json`. T01–T13 validam a sequência operacional e T14–T20 validam erros; T08 é uma negativa de negócio do gateway, com HTTP 201 por registrar a tentativa. As asserções da coleção validam o conteúdo abaixo, além do status HTTP.

| ID | Conteúdo esperado da resposta | Evidência e uso |
|---|---|---|
| T01 | perfil = CLIENTE | Requisição T01; cadastro sem elevação de perfil |
| T02 | accessToken é uma string | Requisição T02; grava token para as próximas chamadas |
| T03 | user.perfil = ADMIN | Requisição T03; grava adminToken em variável local |
| T04 | items[0].produtoId = 1; precoCentavos = 2500 | Requisição T04; depende dos dados fictícios do seed |
| T05 | consentimento = true | Requisição T05; fidelidade facultativa habilitada |
| T06 | status = AGUARDANDO_PAGAMENTO; totalCentavos = 5000; canalPedido = TOTEM | Requisição T06; grava pedidoId |
| T07 | id igual ao pedidoId de T06 | Requisição T07; mesma chave e corpo, sem novo pedido |
| T08 | status = NEGADO | Requisição T08; tentativa recusada registrada |
| T09 | status = APROVADO | Requisição T09; nova chave autoriza a progressão do pedido |
| T10 | status = PRONTO | Requisição T10; usa token administrativo autorizado |
| T11 | status = ENTREGUE | Requisição T11; entrega finaliza o fluxo |
| T12 | saldo = 50 | Requisição T12; 50 reais pagos correspondem a 50 pontos |
| T13 | items contém pedidoId com canalPedido = TOTEM | Requisição T13; filtro por canal e estado entregue |
| T14 | error, message, details, timestamp, path e requestId | Requisição T14; erro 401 sem Authorization |
| T15 | Mesmas seis chaves do padrão de erro | Requisição T15; erro 403 usando token CLIENTE |
| T16 | Mesmas seis chaves do padrão de erro | Requisição T16; erro 422 por ausência do canal |
| T17 | Mesmas seis chaves do padrão de erro | Requisição T17; erro 422 para canal OUTRO |
| T18 | Mesmas seis chaves do padrão de erro | Requisição T18; erro 409 por quantidade superior ao saldo |
| T19 | Mesmas seis chaves do padrão de erro | Requisição T19; erro 404 para pedido inexistente |
| T20 | Mesmas seis chaves do padrão de erro | Requisição T20; erro 409 ao tentar reabrir pedido entregue |

| T21 | Mesmas seis chaves do padrão de erro | Requisição T21; unidade inexistente na criação de pedido |
| T22 | Mesmas seis chaves do padrão de erro | Requisição T22; produto inexistente na criação de pedido |
| T23 | items contém evento PEDIDO_CRIADO | Requisição T23; consulta ADMIN comprova auditoria |

## Testes automatizados complementares

| Teste JUnit | Entrada e pré-condição | Resultado verificado |
|---|---|---|
| auditoriaEOpenApi | Cliente cria pedido; ADMIN consulta GET /auditoria | HTTP 200; registro PEDIDO_CRIADO presente; canalPedido obrigatório no esquema |
| cupomERecursoAusente | Pedido com NORDESTE10; depois unidadeId = 999 | Desconto produz totalCentavos = 4500; unidade ausente retorna 404 |
| quantidadeFracionariaRejeitada | POST /pedidos com quantidade = 1.5 | HTTP 422; inteiro exigido |

Esses três casos são testes Java, não requisições adicionais da coleção Postman. A coleção contém 23 cenários em sete pastas, incluindo auditoria e unidade/produto inexistente. A suíte Java verifica comportamentos adicionais sem substituir os artefatos solicitados pela trilha.
