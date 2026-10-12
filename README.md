# Raízes do Nordeste — Java

Back-end **100% Java 21**, com Spring Boot 3.5.6, Spring Security, JDBC, Flyway e H2 persistente em arquivo. A API atende APP, TOTEM, BALCAO, PICKUP e WEB, com pedidos, estoque por unidade, pagamento mock, perfis, fidelidade e auditoria.

O código da aplicação, os testes e as ferramentas de geração de documentação são Java. Os arquivos `.sh` apenas iniciam Maven/JVM; não há implementação Python ou Node. A coleção Postman contém os testes JavaScript exigidos pelo próprio formato Postman e também é executada por um teste JUnit Java, sem Node instalado.

## Requisitos

- **JDK 21**, incluindo `java` e `javac`.
- **Maven 3.9.11** ou outra versão compatível 3.9+.
- H2 é uma dependência Maven; nenhum banco externo é necessário.
- Bash para os comandos abaixo. Graphviz e fontes Liberation Serif/DejaVu são opcionais, apenas para regenerar diagramas/PDF; artefatos já estão incluídos.

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
| ORDER_RESERVATION_TTL_SECONDS | Prazo de pagamento antes do cancelamento automático; padrão 1800 segundos |
| ORDER_EXPIRATION_SCAN_MS | Intervalo de varredura de reservas vencidas; padrão 60000 milissegundos |
| AUTH_RATE_LIMIT_MAX_REQUESTS | Orçamento combinado de login e cadastro por IP de conexão; padrão 20 |
| AUTH_RATE_LIMIT_WINDOW_SECONDS | Janela do orçamento de autenticação; padrão 60 segundos |

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

Pedidos que continuam em `AGUARDANDO_PAGAMENTO` além do prazo configurado são cancelados automaticamente. A varredura devolve o estoque uma única vez e restitui pontos resgatados quando o consentimento continua ativo. O pagamento também verifica o vencimento dentro da operação transacional e responde HTTP 409 a um pedido expirado; não é preciso esperar a próxima varredura para impedir aprovação tardia. Pedidos pagos não expiram. O vencimento é calculado a partir de `created_at`, por isso continua funcionando após reiniciar a API.

`POST /auth/login` e `POST /usuarios` compartilham um orçamento por IP de conexão. Ao excedê-lo, a API responde HTTP 429 com `Retry-After` em segundos e o envelope de erro habitual. O controle usa memória limitada e janelas por instância; reiniciar a JVM reinicia os contadores. Endereços de `X-Forwarded-For` não são aceitos como identidade do cliente. Uma implantação atrás de proxy precisa definir confiança no proxy e limites no ponto de entrada para evitar que todos os clientes compartilhem o mesmo orçamento.

Métodos HTTP incompatíveis com a rota recebem 405 com `Allow`; corpo com tipo de mídia não suportado recebe 415; `Accept` sem representação disponível recebe 406. Os erros mantêm JSON com `requestId`. Em pagamentos, `status` é o estado atual da tentativa (inclusive `ESTORNADO`) e `payload` preserva a resposta original do gateway mock.

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

A suíte JUnit 5 executa **62 testes**, incluindo 32 testes originais de integração, quatro de erros HTTP, 14 de expiração e concorrência, sete do limite de autenticação, três de documentação, um de migração persistente e um que percorre as **23 requisições da coleção**, com 46 grupos de asserções equivalentes ao fluxo Postman. Não há testes pulados. Os testes HTTP usam servidor real em porta aleatória e H2 em memória exclusivo; o limite de autenticação também usa MockMvc e relógio controlado; a migração usa arquivo H2 em diretório temporário. Nenhum teste toca na base de desenvolvimento. A atualização V1→V2 preserva dados e valida os checksums existentes, sem editar a migration original.

Evidências atuais ficam em `target/surefire-reports` e `target/evidencias-colecao-java.json`. Os resumos sanitizados versionados em `docs/evidencias-*` são históricos e não substituem uma execução da versão atual. A evidência é da versão e da máquina em que os testes rodaram; importar a coleção não comprova sucesso.

Para usar Postman, importe [docs/postman_collection.json](docs/postman_collection.json), configure baseUrl para sua API e adminPassword em ambiente local privado. Execute T01–T23 em ordem; cliente e chave são gerados a cada execução. Não exporte senha/tokens da sessão. A coleção movimenta duas unidades do produto 1 no banco utilizado. Plano: [docs/plano-testes.md](docs/plano-testes.md). Ela também é executada por `CollectionContractTest` em Java; Newman não é necessário.

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

- A [fonte completa do relatório revisado](docs/relatorio.md) está publicada para consulta e rastreabilidade. Preserva **40 tabelas**, **cinco figuras**, evidências da suíte de **62 testes** e a declaração de uso de IA ao final. Os arquivos PDF e Word gerados são mantidos fora do Git.
- [DER PDF](docs/diagramas/der.pdf), [DER SVG](docs/diagramas/der.svg), casos de uso, classes, arquitetura e atividade em `docs/diagramas`.
- [Contratos da API](docs/api.md), [OpenAPI](docs/openapi.json), [coleção](docs/postman_collection.json) e [sequência](docs/sequencia.md).
- LGPD: BCrypt com custo 12, JWT com expiração, controle por perfil/unidade, auditoria sem credenciais, consentimento revogável e desidentificação que invalida os tokens existentes. IDs históricos permanecem, portanto não há garantia de anonimização irreversível em sistemas externos. Retenção automática não está implementada.

A fonte `docs/relatorio.md` reúne o conteúdo completo do PDF corrigido: corpo, referências, contratos, cenários, evidências, diagramas e declaração. `AcademicReport` lê somente essa fonte e as imagens referenciadas; não recompõe apêndices nem substitui evidências documentadas por registros históricos. A execução da API e dos testes não depende da geração do relatório.

Regenerar documentação com API iniciada (Graphviz, Liberation Serif e DejaVu instalados):

```bash
./scripts/mvn.sh test-compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/docs-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/docs-classpath.txt)" br.com.raizes.Documentation
```

`Documentation` atualiza os contratos OpenAPI/Markdown e regenera os cinco diagramas PNG/SVG antes de renderizar `docs/relatorio.md` com PDFBox. Esse comando preserva o texto da fonte completa. Depois de mudar a API ou suas evidências, revise os trechos correspondentes da fonte antes de gerar um novo relatório.

Para regenerar apenas os contratos da API, sem precisar de `docs/relatorio.md`, Graphviz ou fontes:

```bash
./scripts/mvn.sh test-compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/docs-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/docs-classpath.txt)" br.com.raizes.Documentation --api-only
```

Opcionalmente informe a URL-base antes de `--api-only`. O modo completo verifica a presença de `docs/relatorio.md` antes de acessar a API ou atualizar os artefatos. Os exemplos de CLIENTE usam `unidadeId: null`; apenas perfis operacionais recebem vínculo com unidade.

O PDF único inclui capa, folha de rosto, sumário com páginas reais, seções do roteiro, referências, contratos, cenários de testes, evidências, cinco diagramas e declaração. Formatação: A4, margens superior/esquerda de 3 cm e inferior/direita de 2 cm, fonte serifada de 12 pontos e espaçamento de 1,5 no texto; tabelas, códigos e referências usam espaçamento simples. Os diagramas ocupam páginas em paisagem. A numeração conta a partir da folha de rosto e aparece na introdução. Os dados de capa ficam em [docs/identificacao-academica.properties](docs/identificacao-academica.properties); curso, período e identificação do trabalho foram fornecidos pelo estudante; polo e cidade são **Cariacica/ES**. O nome Luciane Yanase Kanashiro foi obtido da capa do roteiro da Trilha Back-End. O manual ABNT institucional completo não foi fornecido e deve ser conferido no AVA.

A [declaração de uso de IA](docs/declaracao-uso-ia.md), incluída ao final da fonte e do relatório, registra exatamente: “Declaro o uso de inteligência artificial, por meio do Word Copilot, para revisão ortográfica, organização do texto.”

A revisão acadêmica mantém o PDF e o Word editável em `output/4937546_Projeto_Back_End_FINAL.PDF` e `output/4937546_Projeto_Back_End_FINAL.docx`, fora do Git. A fonte completa correspondente está em `docs/relatorio.md`; os arquivos locais de edição e acabamento ficam em `output/`. Os diagramas em `docs/diagramas/` incluem o Gateway mock explícito, a separação dos perfis, a expiração de reservas e um diagrama de atividade complementar em `sequencia.dot`. No Word, atualize o campo do sumário após editar ou abrir o documento.

Para conferir o conteúdo da fonte completa por uma renderização Java independente, sem chamar a API nem alterar os diagramas:

```bash
./scripts/mvn.sh test-compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/docs-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/docs-classpath.txt)" br.com.raizes.AcademicReport
```

Esse comando gera `output/4937546_Projeto_Back_End_JAVA.pdf`, com as 40 tabelas, cinco figuras e declaração presentes na fonte publicada. Preserva os PDF e Word finais; sua paginação e acabamento podem diferir da versão final da entrega. As evidências mantêm as datas e o contexto registrados em `docs/relatorio.md`; gerar o PDF não executa os testes.

## Nuvem e publicação

Use o checkout existente; cada tarefa já tem ambiente isolado, sem criar worktrees. Dependências e arquivos podem ser retidos no snapshot, mas a JVM precisa ser reiniciada em uma nova tarefa. `install_script` e `start_skill` são instruções salvas; salvar não executa nem publica o ambiente.

O código Java foi preparado para envio autorizado à branch main de https://github.com/jefersonrivaldo/raizes-do-nordeste . Repositório e coleção foram verificados sem autenticação em 11/10/2026 e retornaram HTTP 200. O acesso público está disponível para avaliação. Não foi feito deploy público ou fabricado histórico acadêmico de commits.

O roteiro fornecido é uma cópia adaptada para estudo. Revise a versão oficial, o código e o relatório; confira a identificação acadêmica e ajuste a formatação exigida antes da entrega. H2 em arquivo é destinado ao estudo, sem alta disponibilidade validada. TLS público, proteção de tráfego distribuída, banco de produção, backup e retenção automática são evoluções propostas. Pagamento e estorno são exclusivamente mock.

A coleção possui sete pastas sequenciais; execute todas em ordem T01–T23. O executor Java percorre pastas recursivamente. O teste de migração cria um H2 em arquivo, grava uma unidade, fecha a conexão e reabre o banco: Flyway valida o checksum, não reaplica V1 e preserva o registro.

Validação adicional da coleção: Newman 6.2.1 executou os scripts Postman reais das sete pastas, com 23 requisições e 46 asserções aprovadas. Resumo sanitizado: [docs/evidencias-postman-newman.json](docs/evidencias-postman-newman.json). O runner externo não é uma dependência da API nem requisito para os testes Java.

## Validação no GitHub

O workflow `.github/workflows/ci.yml` executa `mvn --batch-mode --strict-checksums verify` com Java 21 em pushes para `main` e pull requests, e guarda os relatórios Surefire mesmo quando os testes falham. As actions estão fixadas por SHA e têm permissão de leitura. Dependabot acompanha mensalmente Maven e GitHub Actions; alertas de segurança dependem das configurações do repositório no GitHub.
