# Raízes do Nordeste

API de estudo para uma rede de lanchonetes, construída a partir do roteiro Back-end fornecido. Integra pedidos de APP, TOTEM, BALCAO, PICKUP e WEB, com estoque por unidade, pagamento mock, perfis e fidelidade.

## Requisitos

- Python **3.12**, SQLite incluído na biblioteca padrão; sem servidor de banco externo.
- Bash para os scripts de execução; instalação usa PyPI com TLS e hashes.
- Node.js 24 + Newman 6.2.1 somente para executar a coleção via CLI (Postman pode importá-la sem Node).
- Graphviz e ReportLab somente para regenerar os diagramas/PDF; arquivos já estão incluídos.

## Execução do zero

Dentro do checkout:

```bash
cd /workspace/raizes-do-nordeste
./scripts/install.sh
.venv/bin/python scripts/init_local.py
./scripts/seed.sh
./scripts/start.sh
```

Em uma máquina local, use o caminho do seu clone. O install cria `.venv`, instala o lock com verificação SHA-256 e aplica migrations. `init_local.py` cria `.env` privado com JWT e senha de seed aleatórios, sem imprimir valores ou substituir um arquivo existente. O seed cria duas unidades e três produtos, com 100 unidades de cada produto por loja. Reexecutá-lo **não redefine** senhas/saldos existentes. A API usa a porta 8000 (ou PORT no ambiente).

O Swagger usa a rota **`/docs`**, e o OpenAPI usa **`/openapi.json`** no endereço local da API. Também há documentação offline em [docs/api.md](docs/api.md) e [docs/openapi.json](docs/openapi.json). Healthcheck: `GET /health` deve retornar `{"status":"ok","database":"ok"}`. O onboarding não fornece preview web público.

### Configuração

`.env.example` documenta DATABASE_PATH, JWT_SECRET, TOKEN_TTL_SECONDS e SEED_ADMIN_PASSWORD. Você pode criar `.env` manualmente usando esses nomes. JWT_SECRET precisa de 32+ caracteres; senha administrativa precisa de 12+ caracteres, letras e números. Não use valores de exemplo como segredo real. Os scripts seed/start carregam `.env`; comandos Python diretos usam as variáveis do processo.

- DATABASE_PATH: `data/raizes.sqlite3`, relativo à raiz do checkout.
- JWT_SECRET: chave de assinatura local. É preservada nos reinícios; trocá-la invalida os tokens anteriores.
- TOKEN_TTL_SECONDS: 3600 por padrão.
- SEED_ADMIN_PASSWORD: usada somente ao criar `admin@raizes.local`. A senha aleatória fica no `.env` privado; consulte-a **localmente**, sem publicar arquivo ou exportar credenciais da coleção.

Não há credenciais externas nem processamento real de pagamento. `.env`, `.venv` e bancos locais estão ignorados pelo Git. Em publicação/snapshot de desenvolvimento, a chave local pode ser retida no filesystem privado; a configuração salva não contém seus valores. Nunca reutilize os segredos de estudo em produção.

## Fluxo funcional

1. `POST /usuarios` com nome, email e senha cria CLIENTE; perfil administrativo não pode ser escolhido no cadastro público.
2. `POST /auth/login` retorna JWT. No Swagger, use Authorize com o accessToken. Fora dele, use `Authorization: Bearer <token>`.
3. Consulte `/unidades/1/cardapio` para preços/disponibilidade.
4. `POST /pedidos` exige `Idempotency-Key` (8–100 caracteres) e canalPedido. Exemplo:

```json
{"unidadeId":1,"canalPedido":"TOTEM","itens":[{"produtoId":1,"quantidade":2}],"formaPagamento":"MOCK"}
```

5. `POST /pedidos/{id}/pagamentos`, com nova Idempotency-Key, recebe `{"cenario":"APROVAR"}`. NEGAR/INDISPONIVEL simulam falhas persistidas. Apenas aprovação muda o pedido para EM_PREPARO.
6. Cozinha/equipe autorizada marca PRONTO e atendimento marca ENTREGUE por PATCH `/pedidos/{id}/status`.
7. Com consentimento ativo, a entrega credita 1 ponto por real do total pago. Resgate de 100 pontos reduz R$ 10 no pedido; use pontosResgate múltiplo de 100. Cupom NORDESTE10 dá 10% em subtotal mínimo de R$ 50.

A criação baixa estoque e reserva pontos em uma transação. Cancelamento devolve estoque uma vez e estorna pagamento mock aprovado. Cliente só pode cancelar pedido não pago. Os preços históricos permanecem nos itens. Dinheiro é representado em centavos inteiros. NEGADO/ERRO retorna HTTP 201 porque a tentativa foi registrada; o resultado do gateway está no corpo. Reenvio idempotente devolve o recurso existente no estado atual.

## Perfis e unidades

| Perfil | Permissões |
|---|---|
| CLIENTE | Pedidos próprios, pagamento próprio, cancelamento antes de pagar, fidelidade e desidentificação própria |
| ATENDENTE | Criar/pagar pedidos para clienteId na unidade vinculada; operar status/cancelar |
| COZINHA | Consultar pedidos da unidade e marcar PRONTO |
| GERENTE | Atendimento e estoque da própria unidade; atualização de sua unidade |
| ADMIN | Toda a rede, catálogo, unidades, atribuição de perfis e auditoria |

Crie conta para empregado por POST /usuarios; autenticado como ADMIN, faça PUT `/usuarios/{id}/perfil` com `{"perfil":"COZINHA","unidadeId":1}`. Perfis operacionais exigem unidade. ADMIN e CLIENTE não recebem vínculo operacional. Usuário que já possui pedidos deve permanecer CLIENTE. Empregado informa clienteId na criação de pedido. Não há fluxo anônimo de cliente de totem neste MVP.

## Testes

### Automatizados

```bash
.venv/bin/python -m pytest -q
```

Os testes usam bancos temporários e segredos fictícios; não alteram o banco de desenvolvimento. Há 28 cenários, incluindo disputa pelo último estoque, idempotência concorrente, limites de perfil/unidade, pagamento negado, revogação de consentimento e desidentificação. Evidência da execução preparada: [docs/evidencias-pytest.xml](docs/evidencias-pytest.xml).

### Postman / Newman

Importe [docs/postman_collection.json](docs/postman_collection.json). Configure baseUrl para a API local e **adminPassword em um ambiente local privado**, com a senha definida no seed. A coleção cria cliente fictício novo por execução, faz login e guarda os tokens em variáveis; rode T01–T20 em ordem. Não exporte variáveis/token/senha da sessão para o repositório. Plano e entradas: [docs/plano-testes.md](docs/plano-testes.md).

Via CLI, sem passar senha como argumento:

```bash
npm install --prefix /workspace/onboarding-tools --cache /workspace/.npm-cache --no-audit --no-fund newman@6.2.1
NODE_PATH=/workspace/onboarding-tools/node_modules node scripts/run_collection.cjs
```

O executor lê apenas a senha necessária do `.env` local ou SEED_ADMIN_PASSWORD do processo. API_BASE_URL pode alterar o destino local. Ele grava um resumo sem credenciais em [docs/evidencias-postman.json](docs/evidencias-postman.json). A coleção movimenta estoque real da base em uso (duas unidades do produto 1 por execução); para testes repetidos extensivos, use banco exclusivo. Não redefina estoque de desenvolvimento indiscriminadamente. O teste T18 pressupõe estoque inferior a 1000 unidades.

## Estrutura e documentação

```text
app/domain/          enums, transições, descontos e erros de negócio
app/application/     casos de uso, transações e autorização por recurso
app/infrastructure/  SQLite, migrations, seed, segurança e gateway mock
app/api/             rotas, contratos, autorização por perfil e exemplos
migrations/          SQL versionado com FK, CHECK e índices
tests/               suíte isolada de integração e concorrência
scripts/             instalação, inicialização e geração de artefatos
docs/                relatório, contratos, coleção, evidências e diagramas
```

- [Relatório técnico PDF](docs/relatorio-tecnico.pdf) e [fonte editável](docs/relatorio.md).
- [DER em PDF](docs/diagramas/der.pdf), [SVG ampliável](docs/diagramas/der.svg) e fonte DOT; compatíveis com a migration.
- [Casos de uso](docs/diagramas/casos-de-uso.svg), [classes conceituais](docs/diagramas/classes.svg), [arquitetura](docs/diagramas/arquitetura.svg) e [sequência](docs/sequencia.md).
- Contratos completos no Swagger e docs/api.md; padrão de erro uniforme, paginação e exemplos.
- LGPD e justificativas em docs/relatorio.md, incluindo dados/finalidades, consentimento, desidentificação, auditoria e retenção proposta.

Para regenerar artefatos, mantenha o banco migrado e execute da raiz:

```bash
.venv/bin/python -m scripts.generate_collection
.venv/bin/python -m scripts.generate_docs
# Opcional: após instalar Graphviz, fontes DejaVu e requirements-docs.txt em seu ambiente Python:
python -m scripts.generate_report
```

A geração de contratos não modifica o banco de negócio. Graphviz é necessário para gerar os diagramas. Para instalar dependências opcionais de PDF no venv: `.venv/bin/python -m pip install -r requirements-docs.txt`, depois `.venv/bin/python -m scripts.generate_report`. A máquina de onboarding já dispõe dessas ferramentas no Python do sistema.

## Limites e entrega acadêmica

API e banco foram verificados nesta máquina; isso não comprova publicação ou restauração em nova tarefa. SQLite serializa escritas, não foi feito benchmark para rede real e não há alta disponibilidade. Rate limiting, TLS público, migração para PostgreSQL, expiração automática de pedidos não pagos, refresh/logout individual e retenção automática são evoluções. Nenhum cartão é coletado; gateway e estorno são mock. Desidentificação mantém IDs históricos, portanto não garante anonimização irreversível fora desta base.

O PDF de referência é uma **cópia adaptada para estudo**. Confira a versão oficial e revise a implementação; preencha seus dados acadêmicos e ajuste formatação institucional antes da entrega. Não foi fabricado histórico de cinco commits nem realizado deploy. Repositório pretendido: https://github.com/jefersonrivaldo/raizes-do-nordeste . O código e o relatório foram enviados à branch main após autorização do proprietário. O acesso aos links sem login retornou HTTP 404 nesta sessão; entre na conta GitHub com acesso ao repositório para baixar os arquivos. A visibilidade pública ainda precisa ser verificada pelo proprietário.
