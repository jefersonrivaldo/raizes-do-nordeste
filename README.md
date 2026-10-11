# Raízes do Nordeste — Java

Back-end **100% Java 21**, com Spring Boot 3.5.6, Spring Security, JDBC, Flyway e H2 persistente em arquivo. A API atende APP, TOTEM, BALCAO, PICKUP e WEB, com pedidos, estoque por unidade, pagamento mock, perfis, fidelidade e auditoria.

O código da aplicação, os testes e as ferramentas de geração de documentação são Java. Os arquivos `.sh` apenas iniciam Maven/JVM; não há implementação Python ou Node. A coleção Postman contém os testes JavaScript exigidos pelo próprio formato Postman e também é executada por um teste JUnit Java, sem Node instalado.

## Requisitos

- **JDK 21**, incluindo `java` e `javac`.
- **Maven 3.9.11** ou outra versão compatível 3.9+.
- H2 é uma dependência Maven; nenhum banco externo é necessário.
- Bash para os comandos abaixo. Graphviz e fontes DejaVu são opcionais, apenas para regenerar diagramas/PDF; artefatos já estão incluídos.

## Executar

Na raiz do checkout:

```bash
# No ambiente da nuvem, se JDK/Maven ainda não estiverem preparados:
./scripts/toolchains.sh
./scripts/mvn.sh verify
# Cria configuração local privada sem sobrescrever .env existente:
java scripts/InitLocal.java
./scripts/start.sh
```

Na máquina de onboarding, use o Java do JDK instalado caso seu comando `java` aponte para outra versão:

```bash
/workspace/toolchains/jdk/jdk-21.0.8+9/bin/java scripts/InitLocal.java
```

Em máquina local com JDK 21 e Maven, também pode usar `mvn verify` e `java -jar target/raizes-do-nordeste-1.0.0.jar` depois de carregar as variáveis do `.env` no processo. A JVM não lê `.env` automaticamente; `scripts/start.sh` faz isso. No Linux/Bash: `set -a; source .env; set +a`.

O Maven instala dependências, compila, executa os testes e gera o JAR. A aplicação aplica migrations Flyway e seed idempotente no início. O seed cria `admin@raizes.local`, duas unidades e três produtos, com 100 unidades de estoque por produto/unidade. Não redefine senhas nem saldos existentes. O banco de desenvolvimento fica em `data/raizes.mv.db` e sobrevive a reinícios.

Swagger: rota **`/docs`** (redireciona para Swagger UI). OpenAPI: **`/openapi.json`**. Healthcheck: `GET /health` retorna `{"status":"ok","database":"ok"}`. Porta padrão 8000; pode ser alterada por PORT. Não há preview web público no onboarding.

## Variáveis e credenciais

Veja `.env.example`. `scripts/InitLocal.java` gera JWT_SECRET e SEED_ADMIN_PASSWORD aleatórios em `.env` privado (0600 quando suportado), sem exibir valores. Arquivo existente é preservado.

| Variável | Uso |
|---|---|
| JWT_SECRET | Segredo de pelo menos 32 bytes, obrigatório para assinar JWT |
| TOKEN_TTL_SECONDS | Expiração de 1 a 86400 segundos; padrão 3600 |
| SEED_ADMIN_PASSWORD | Criação inicial do ADMIN; 12 a 72 bytes, letras e números; consulte apenas localmente |
| DATABASE_URL | Padrão `jdbc:h2:file:./data/raizes;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000` |
| DATABASE_PASSWORD | Opcional para banco local; para outro ambiente configure conforme sua política |
| SEED_ENABLED | true por padrão; false desativa o seed |
| PORT | 8000 por padrão |

Nunca publique `.env`, banco local ou tokens. Segredos não são embutidos no código/JAR nem na configuração salva da nuvem. Não existem serviços externos de pagamento ou dados de cartão. O usuário `sa` do H2 é apenas conexão interna; o console H2 não está exposto.

## Fluxo principal

1. `POST /usuarios` cadastra CLIENTE; não aceita perfil escolhido pelo público.
2. `POST /auth/login` retorna accessToken. Use `Authorization: Bearer <token>`; no Swagger, informe o token em Authorize.
3. Consulte `/unidades/1/cardapio`.
4. Crie pedido em `POST /pedidos`, com `Idempotency-Key` de 8–100 caracteres:

```json
{"unidadeId":1,"canalPedido":"TOTEM","itens":[{"produtoId":1,"quantidade":2}],"formaPagamento":"MOCK"}
```

5. `POST /pedidos/{id}/pagamentos`, com nova chave, recebe `{"cenario":"APROVAR"}`. NEGAR e INDISPONIVEL simulam falhas. HTTP 201 significa tentativa persistida; examine status APROVADO/NEGADO/ERRO no corpo.
6. Apenas aprovação leva a EM_PREPARO. Cozinha/equipe marca PRONTO; atendimento/gerente/admin marca ENTREGUE via PATCH `/pedidos/{id}/status`.
7. Consentimento facultativo em `/fidelidade/consentimento`: 1 ponto por real efetivamente pago é creditado na entrega. Resgate de 100 pontos reduz R$ 10, usando pontosResgate múltiplo de 100. Cupom NORDESTE10 dá 10% sobre subtotal mínimo de R$ 50.

Pedidos baixam estoque e debitam resgate dentro da mesma transação. Locks por usuário/pedido/estoque impedem efeitos duplicados e venda além do saldo. Itens são bloqueados em ordem de produto para reduzir disputas. Dinheiro usa `long` em centavos. Cancelamento devolve estoque uma vez e estorna pagamento mock aprovado. Cliente só cancela enquanto aguarda pagamento; cozinha apenas marca PRONTO. Pedido entregue é terminal. Reenvio da mesma chave/corpo retorna o recurso no estado atual.

## Perfis

| Perfil | Permissões |
|---|---|
| CLIENTE | Recursos próprios, pagamento, cancelamento antes de pagar, fidelidade e desidentificação |
| ATENDENTE | Atendimento de clientes da unidade vinculada, pagamentos e status |
| COZINHA | Consulta da unidade e marcação PRONTO |
| GERENTE | Atendimento e estoque da unidade; atualização de sua unidade |
| ADMIN | Toda a rede, unidades, catálogo, perfis e auditoria |

Crie uma conta de empregado por POST /usuarios. Como ADMIN, atribua perfil com PUT `/usuarios/{id}/perfil`, corpo `{"perfil":"COZINHA","unidadeId":1}`. Perfis operacionais exigem unidade; ADMIN/CLIENTE não recebem vínculo. Empregado informa clienteId na criação do pedido. Cliente com pedidos deve manter perfil CLIENTE. Não existe atendimento anônimo de totem neste MVP.

## Testes — todos em Java

```bash
./scripts/mvn.sh verify
# Alternativa em máquina com Maven/JDK configurados:
mvn verify
```

A suíte JUnit 5 executa 31 testes de integração e um teste que percorre as **20 requisições da coleção**, com 40 grupos de asserções equivalentes ao fluxo Postman: **32 testes JUnit**, sem testes pulados. Usa servidor HTTP real em porta aleatória e banco H2 em memória exclusivo, sem tocar na base de desenvolvimento. Cobre concorrência, idempotência, 401/403/422/409, estoque, negativas/indisponibilidade de pagamento, consentimento, anonimização e repetição de seed.

Evidências ficam em `target/surefire-reports`; resumos sanitizados desta execução estão em `docs/evidencias-junit-api.xml`, `docs/evidencias-junit-colecao.xml` e `docs/evidencias-colecao-java.json`. A evidência é da versão e da máquina em que os testes rodaram; importar a coleção não comprova sucesso.

Para usar Postman, importe [docs/postman_collection.json](docs/postman_collection.json), configure baseUrl para sua API e adminPassword em ambiente local privado. Execute T01–T20 em ordem; cliente e chave são gerados a cada execução. Não exporte senha/tokens da sessão. A coleção movimenta duas unidades do produto 1 no banco utilizado. Plano: [docs/plano-testes.md](docs/plano-testes.md). Ela também é executada por `CollectionContractTest` em Java; Newman não é necessário.

## Organização e entrega

```text
src/main/java/br/com/raizes/
  domain/          enums, ator, transições, descontos e erros de negócio
  application/     casos de uso transacionais e autorização por recurso
  infrastructure/  JDBC, JWT, Spring Security, seed e gateway mock
  api/             controllers, DTOs, validação, Swagger e erros
src/main/resources/db/migration/  migrations Flyway SQL
src/test/java/br/com/raizes/      integração HTTP, coleção e documentação Java
scripts/                         ferramentas Java e comandos de Maven/JVM
```

- [Relatório técnico PDF](docs/relatorio-tecnico.pdf) e [fonte](docs/relatorio.md).
- [DER PDF](docs/diagramas/der.pdf), [DER SVG](docs/diagramas/der.svg), casos de uso, classes e arquitetura em `docs/diagramas`.
- [Contratos da API](docs/api.md), [OpenAPI](docs/openapi.json), [coleção](docs/postman_collection.json) e [sequência](docs/sequencia.md).
- LGPD: BCrypt com custo 12, JWT com expiração, controle por perfil/unidade, auditoria sem credenciais, consentimento revogável e desidentificação que invalida os tokens existentes. IDs históricos permanecem, portanto não há garantia de anonimização irreversível em sistemas externos. Retenção automática não está implementada.

Regenerar documentação com API iniciada (Graphviz e DejaVu instalados):

```bash
./scripts/mvn.sh test-compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/docs-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/docs-classpath.txt)" br.com.raizes.Documentation
```

`Documentation` é Java e usa PDFBox. Não há scripts Python de geração de PDF.

## Nuvem e publicação

Use o checkout existente; cada tarefa já tem ambiente isolado, sem criar worktrees. Dependências e arquivos podem ser retidos no snapshot, mas a JVM precisa ser reiniciada em uma nova tarefa. `install_script` e `start_skill` são instruções salvas; salvar não executa nem publica o ambiente.

O código Java foi preparado para envio autorizado à branch main de https://github.com/jefersonrivaldo/raizes-do-nordeste . Os links de download sem login retornaram 404 nesta sessão; entre na sua conta GitHub com acesso ao repositório. A visibilidade pública para avaliação ainda deve ser verificada pelo proprietário. Não foi feito deploy público ou fabricado histórico acadêmico de commits.

O roteiro fornecido é uma cópia adaptada para estudo. Revise a versão oficial, o código e o relatório; confira a identificação acadêmica e ajuste a formatação exigida antes da entrega. H2 em arquivo é destinado ao estudo, sem alta disponibilidade validada. TLS público, rate limiting, banco de produção, backup, expiração de reservas e retenção automática são evoluções propostas. Pagamento e estorno são exclusivamente mock.
