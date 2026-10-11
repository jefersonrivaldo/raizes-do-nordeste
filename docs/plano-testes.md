# Plano de testes executável

Executar na ordem indicada com banco criado pelo seed. Cada cenário tem asserções na coleção. CLIENTE é criado em cada execução; adminPassword é configurado localmente e não deve ser exportado. Unidade 1 e produto 1 vêm do seed.

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
