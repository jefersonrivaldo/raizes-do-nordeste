# Raízes do Nordeste — relatório técnico de estudo

Autor: Jeferson Rivaldo Santos

RU: 4937546

## 1. Introdução e objetivos

A rede de lanchonetes precisa integrar unidades com cardápio e estoque próprios, atendendo APP, TOTEM, BALCAO, PICKUP e WEB. Esta implementação acadêmica oferece uma API REST persistente que centraliza pedidos, pagamento simulado, operação da cozinha e fidelização. O canal de origem é obrigatório e consultável, permitindo acompanhamento operacional. O objetivo é fechar o fluxo pedido → pagamento mock → preparo → entrega, com validação de estoque e controle de acesso.

Este relatório considera o Estudo de Caso e a Trilha Back-End da rede Raízes do Nordeste (UNINTER, 2026), nas versões editadas para estudo fornecidas pelo estudante. A solução prioriza o MVP executável e a documentação das decisões. Os atores são cliente, atendente, cozinha, gerente, administrador e gateway de pagamento simulado. Antes da entrega institucional, as orientações devem ser conferidas nos documentos oficiais completos e no manual acadêmico disponível no AVA.

## 2. Análise e requisitos

| ID | Requisito | Prioridade e evidência |
|---|---|---|
| RF01 | Cadastro e autenticação por token; autorização por perfil | Essencial; /usuarios, /auth/login; hash BCrypt e JWT |
| RF02 | Gestão da rede/unidades | Essencial; cadastro/atualização ADMIN; gerente atualiza sua unidade |
| RF03 | Catálogo global e cardápio por unidade | Essencial; produtos, preços em centavos e disponibilidade |
| RF04 | Criação e consulta de pedidos com itens e canalPedido | Essencial; canal obrigatório e filtro por canal/status |
| RF05 | Atualização de status e cancelamento | Essencial; máquina de estados com autorização operacional |
| RF06 | Estoque por unidade e movimentos | Essencial; operações atômicas e proibição de saldo negativo |
| RF07 | Pagamento externo simulado e registro de retorno | Essencial; gateway desacoplado, aprovação, negativa, erro e estorno mock |
| RF08 | Fidelidade com consentimento e resgate | Implementado; 1 ponto por real efetivamente pago na entrega; resgate de 100 pontos = R$ 10 |
| RF09 | Promoção/campanha | Implementado; cupom NORDESTE10: 10% sobre subtotal mínimo de R$ 50, sem vigência dinâmica |
| RF10 | Privacidade e auditoria | Implementado; leitura de dados próprios, retirada de consentimento e desidentificação |
| RF11 | Indicadores consolidados da matriz | Proposto; vendas por unidade/região, produtos mais vendidos e relatórios financeiros, com acesso restrito à gestão |
| RF12 | Variações e sazonalidade do cardápio | Parcial; disponibilidade e preços locais implementados; vigência sazonal e receitas regionais propostas |
| RF13 | Campanhas segmentadas | Proposto; segmentação por frequência com consentimento específico; coleta de idade não integra o MVP |
| RF14 | Regras de funcionamento por unidade | Proposto; horários, capacidade da cozinha e metas operacionais demandam modelagem adicional |

| ID | Requisito não funcional | Decisão e limite |
|---|---|---|
| RNF01 | Segurança | JWT HS256 com expiração, hash BCrypt, perfis e vínculo com unidade; HTTPS deve ser configurado no deploy |
| RNF02 | Integridade | FK/CHECK/UNIQUE e transação Spring JDBC com bloqueios FOR UPDATE; estoque, pontos e pedido confirmam juntos |
| RNF03 | Rastreabilidade | Auditoria de operações sensíveis e leituras; requestId por resposta; sem senha/token nos registros |
| RNF04 | Documentação | Swagger /docs, OpenAPI exportado e contratos de entrada/saída tipados |
| RNF05 | Desempenho | Paginação até 100 registros e índices de busca; testes de concorrência; sem alegar benchmark de produção |
| RNF06 | Disponibilidade | Healthcheck com consulta real ao banco; H2 local não oferece alta disponibilidade |
| RNF07 | Tolerância a falhas | Mock indisponível gera registro ERRO; nova chave permite nova tentativa; não autoriza cozinha antes de aprovação |
| RNF08 | Reprodutibilidade | Java 21, versões Maven fixadas e checksums estritos, migrations versionadas, seed idempotente e testes isolados |

Escolheu-se H2 para dispensar um serviço externo no ambiente de estudo. Locks transacionais por recurso protegem estoque e fidelidade; essa decisão limita a concorrência de uma rede em produção. Uma evolução real exige PostgreSQL, controle de concorrência, filas, backups, monitoramento e medição de carga. A integração mock foi escolhida por exigência do roteiro e não coleta dados de cartão.

A priorização atende primeiro à venda consistente e rastreável, evitando estoque negativo e pagamento duplicado. Os indicadores gerenciais, a sazonalidade e as campanhas segmentadas são relevantes ao caso, mas ficam como evolução explicitamente documentada. Alta disponibilidade e desempenho em pico são requisitos de produção; o projeto apresenta mecanismos básicos e uma proposta de evolução, sem afirmar validação de carga ou disponibilidade contínua.

## 3. Modelagem e arquitetura

Os diagramas estão em `docs/diagramas/`: casos de uso, DER completo, classes conceituais e arquitetura em SVG/PNG, com fontes DOT. A sequência do fluxo crítico está também em `docs/sequencia.md`.

A API valida DTOs Java com Bean Validation e aplica autenticação. Application orquestra transações e gateway. Domain define enums, transições, preços e regras de resgate/cupom. Infrastructure contém H2, migrations Flyway SQL, hashing, JWT e gateway simulado. O domínio é implementado com funções e registros persistentes; o diagrama de classes é uma visão conceitual dessas entidades, não uma alegação de ORM ou DDD completo.

Uma unidade possui vários produtos em estoque, e cada produto pode estar em várias unidades; `stocks` usa PK composta (unidade_id, produto_id). Usuário possui pedidos, movimentos de fidelidade e auditoria. Pedido pertence a uma unidade e possui itens e tentativas de pagamento independentes. Itens guardam o nome/preço histórico do produto. Chaves de idempotência são únicas por ator e operação. A migration `V1__initial.sql` define todas as tabelas, PK, FK, CHECK e índices.

| Tabela | Chaves e relacionamento | Integridade relevante |
|---|---|---|
| units / products | PK id; cadastros da unidade e do catálogo | Desativação preserva o histórico |
| users | PK id; FK unidade_id opcional para equipe | Email único; perfil limitado; pontos não negativos |
| stocks | PK composta unidade_id/produto_id; FK para units/products | Quantidade não negativa; preço positivo |
| orders | PK id; FK cliente_id/unidade_id | Canal e status limitados por CHECK; total não negativo |
| order_items | PK id; FK pedido_id/produto_id | Produto único por pedido; quantidade e preço positivos |
| payments | PK id; FK pedido_id | Várias tentativas por pedido; status e payload persistidos |
| stock_movements | PK id; FK composta para stocks; FK ator e pedido | Movimentação vinculada ao recurso e ao responsável |
| loyalty_movements | PK id; FK user_id; FK pedido_id opcional | Entradas e débitos rastreáveis |
| audit | PK id; FK actor_id | Ação, recurso e instante; sem credenciais |
| idempotency | PK ator/operação/chave; FK actor_id | Fingerprint impede reuso da chave com outro conteúdo |

### Feature crítica: realizar pedido e solicitar pagamento

Atores: Cliente (APP/WEB/TOTEM/PICKUP), Atendente (BALCAO), Cozinha, Gerente/Administrador e Gateway mock.

Pré-condições: usuário ativo autenticado; unidade ativa; produtos ativos, ofertados e disponíveis; cliente válido; chave Idempotency-Key; canal válido. Atendente/gerente informa clienteId e só atua na unidade vinculada. Cliente atua em seus próprios pedidos.

Fluxo principal:
1. A API valida canalPedido, itens e quantidades. O servidor calcula preços e descontos, sem confiar em valores monetários enviados pelo cliente.
2. Uma transação verifica estoque e consentimento/saldo para resgate; cria pedido e itens; registra saídas de estoque e eventual débito de pontos; grava auditoria e chave de idempotência.
3. Pedido inicia em AGUARDANDO_PAGAMENTO. O cliente solicita pagamento mock com chave de idempotência própria.
4. O gateway simulado responde com status e payload. A aplicação persiste a tentativa; apenas APROVADO altera o pedido para EM_PREPARO.
5. Cozinha ou atendimento marca PRONTO. Atendimento/gerente/admin marca ENTREGUE.
6. Na entrega, cliente com consentimento ativo recebe `totalCentavos // 100` pontos; reentrega não pode creditar novamente.

Pós-condições: pedido e canal consultáveis, estoque atualizado, pagamento registrado e auditoria persistida. Saldo de pontos só aumenta após entrega.

Exceções: 401 sem autenticação; 403 perfil/unidade/cliente incompatível; 404 recurso inexistente; 422 canal ausente/inválido ou itens repetidos; 409 estoque/pontos insuficientes, cupom inválido, transição proibida ou chave reutilizada com corpo diferente. NEGADO e ERRO são resultados persistidos do gateway (HTTP 201 porque a tentativa foi criada), sem avanço do status. Nova tentativa usa nova chave. Reenvio com mesma chave/corpo devolve o recurso existente no estado atual, sem repetir efeitos.

Cancelamento antes da entrega devolve estoque exatamente uma vez; pagamento aprovado recebe ESTORNADO mock. Cliente pode cancelar apenas enquanto aguarda pagamento; equipe de atendimento autorizada pode cancelar EM_PREPARO/PRONTO. Cozinha pode somente marcar PRONTO. Pedido entregue não pode ser cancelado no MVP. Pontos resgatados voltam no cancelamento se o consentimento ainda estiver ativo. Revogação de consentimento zera o saldo e impede novos créditos e devoluções enquanto revogado; o benefício já aplicado ao pedido não é removido.

Pedidos não pagos mantêm reserva de estoque até cancelamento manual. Expiração automática, reembolso real, compra anônima em totem, login refresh/logout com revogação individual e campanhas dinâmicas são evoluções propostas; não foram implementadas.

## 4. API e contratos

Consulte `docs/api.md` e `docs/openapi.json`; na API em execução, /docs e /openapi.json. Cada endpoint tem parâmetros, contratos tipados, permissões descritas e exemplos. Operações administrativas usam cadastro de cliente seguido de atribuição de perfil pelo ADMIN. Não há elevação de perfil pelo cadastro público.

Padrão de erro: `{error, message, details:[{field,issue}], timestamp, path, requestId}`. Detalhes de validação omitem valores de entrada para não repetir senhas. Listagens retornam `{items,page,limit}`. Dinheiro usa centavos inteiros, evitando erro de ponto flutuante. IDs históricos são preservados; recursos são desativados com ativo=false, sem apagar vendas.

Status: AGUARDANDO_PAGAMENTO → EM_PREPARO (apenas pagamento aprovado) → PRONTO → ENTREGUE. CANCELADO é terminal. Não há salto direto de AGUARDANDO_PAGAMENTO para PRONTO/ENTREGUE.

## 5. LGPD, privacidade, segurança e auditoria

Dados coletados: nome, email e hash de senha para identificação/autenticação; identificador interno, itens, unidade, canal, preços e datas para executar e acompanhar pedidos; perfil e unidade de funcionários para autorização; consentimento (estado, versão 1.0, instante) e movimentos de pontos para fidelização. Não são coletados CPF, telefone, endereço residencial ou dados de cartão. O endereço da unidade é comercial/fictício no seed.

Finalidade e bases legais propostas: execução de contrato para pedidos e conta; consentimento específico facultativo para fidelidade; logs estritamente necessários à segurança devem ter base e retenção revisadas pelo controlador. A descrição técnica não constitui parecer jurídico. A fidelidade é opcional e consentimento pode ser retirado por PUT /fidelidade/consentimento. O saldo é zerado em retirada, com movimento de histórico e evento de auditoria. A versão e o instante do consentimento/revogação são persistidos; o histórico de alterações consta na auditoria.

Senhas: BCrypt com salt aleatório por usuário (custo 12); comparação em tempo constante; jamais retornadas. JWT: HS256, sub/iat/exp/iss/aud obrigatórios, expiração padrão de 3600 segundos e segredo aleatório local de 256 bits. O perfil ativo é consultado no banco a cada requisição, portanto alteração de perfil e desativação se aplicam a tokens já emitidos. A chave privada .env tem permissões 0600 e está ignorada pelo Git.

Autorização: clientes acessam somente recursos próprios; empregados só recursos da unidade atribuída; ADMIN administra a rede. Logs de leitura de perfil/pedidos/pagamentos/fidelidade/auditoria e eventos de criação/status/estoque/consentimento usam apenas IDs e ação, sem credenciais, email, corpo de requisição ou dados de cartão. O endpoint de auditoria é exclusivo do ADMIN.

DELETE /usuarios/me exige perfil CLIENTE e nenhum pedido em aberto. Nome/email/hash são substituídos, consentimento retirado, saldo zerado e conta desativada; tokens antigos deixam de funcionar. IDs e histórico operacional permanecem: trata-se de desidentificação/pseudonimização local, e não garantia de anonimização irreversível em sistemas externos.

Estratégia de retenção proposta: revisar periodicamente contas inativas e necessidade de dados; propor 180 dias para auditoria de desenvolvimento, ajustados à finalidade e obrigações aplicáveis; manter registros de venda pelo prazo definido pelo controlador. Limpeza por prazo não é automática no MVP. Exportação pessoal, gestão de solicitações LGPD e descarte de backups exigem processo operacional futuro.

Antes de produção: definir política de retenção aplicável, HTTPS, armazenamento central de segredos, proteção contra força bruta/rate limiting, controle de backup/restauração e segregação de dados. O presente ambiente é destinado ao estudo e não valida capacidade/segurança de operação pública.

## 6. Plano de testes e evidências

A coleção Postman possui 23 cenários ordenados em sete pastas, positivos e negativos, com testes de status e conteúdo. `docs/plano-testes.md` apresenta entrada, pré-condições e resultado esperado de cada cenário; `docs/evidencias-colecao-java.json` é o resumo sanitizado da execução real em Java via JUnit e HttpClient. A suíte JUnit 5 possui 33 testes em banco temporário, incluindo concorrência de estoque/idempotência, isolamento entre clientes/unidades, pagamento negado/indisponível, consentimento, anonimização, migração em banco persistente existente e repetição de seed. `docs/evidencias-junit-api.xml`, `docs/evidencias-junit-colecao.xml` e `docs/evidencias-junit-migracao.xml` registram a execução atual.

Os resultados são evidência da máquina atual e do código correspondente; novas alterações exigem nova execução. Importar a coleção não prova aprovação dos cenários. Contagem de testes, falhas e data real constam nos arquivos de evidência. Uma validação adicional com Newman 6.2.1 executou os scripts Postman reais: 23 requisições e 46 asserções aprovadas, sem falhas; resumo em docs/evidencias-postman-newman.json. Testes não publicam o ambiente nem validam restauração de snapshot em uma nova tarefa.

## 7. Entrega técnica

Os artefatos incluem código Java, migrations Flyway, scripts de JVM/Maven, pom.xml, seed, .env.example, README, OpenAPI, coleção, plano de testes e diagramas. O documento único incorpora contratos, cenários, evidências sanitizadas e diagramas nos apêndices/anexos. Repositório: https://github.com/jefersonrivaldo/raizes-do-nordeste . A referência da revisão técnica é a branch main no commit 0756613 como base anterior às correções de coleção e migrações desta revisão. O conteúdo testado teve 33 testes aprovados, sem falhas ou testes ignorados.

Procedimento de reprodução: instalar JDK 21 e Maven 3.9+, executar ./scripts/mvn.sh verify, gerar a configuração local com java scripts/InitLocal.java e iniciar ./scripts/start.sh. A aplicação aplica Flyway e seed automaticamente; não é necessário criar um serviço H2 separado. O banco persistente fica em data/raizes.mv.db. Variáveis privadas são descritas em .env.example; o arquivo .env não deve ser publicado.

Swagger local: http://localhost:8000/docs ; contrato OpenAPI: http://localhost:8000/openapi.json ; coleção: https://github.com/jefersonrivaldo/raizes-do-nordeste/blob/main/docs/postman_collection.json . As URLs locais são acessadas depois de iniciar a API na máquina do avaliador. Na coleção, definir baseUrl e adminPassword em ambiente privado, executar T01–T23 em ordem e usar o seed documentado. As instruções completas estão no README.

Situação de acesso: repositório e coleção retornaram HTTP 200 sem autenticação na revisão de 11 out. 2026. O acesso público está disponível para avaliação. O ambiente local foi validado; não foi realizado deploy público. A apresentação de uma URL no relatório não comprova que ela esteja acessível ao avaliador.

## 8. Conclusão

O fluxo crítico é implementado com persistência real e operações transacionais. As limitações são explicitadas para separar capacidades demonstradas de evoluções propostas. Para entrega acadêmica, o estudante deve revisar o código/relatório, conferir seus dados de identificação, verificar a versão oficial do roteiro, verificar a visibilidade do repositório e testar os links pelo acesso do avaliador. Histórico mínimo de cinco commits é uma recomendação do roteiro; não foi fabricado nesta implementação.

## REFERÊNCIAS

UNINTER. Projeto Multidisciplinar: orientações e estudo de caso — rede Raízes do Nordeste. Versão editada para estudo, 2026. Arquivo fornecido pelo estudante: UNINTER_Estudo_de_Caso_Versao_Editada_Estudo.pdf. Contém omissões; o documento oficial prevalece.

UNINTER. Projeto Multidisciplinar: trilha Back-End — rede Raízes do Nordeste. Versão editada para estudo, 2026. Arquivo fornecido pelo estudante: UNINTER_Trilha_Back_End_Versao_Editada_Estudo.pdf. Contém omissões; o documento oficial prevalece.

BRASIL. Lei nº 13.709, de 14 de agosto de 2018 (LGPD). https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm . Referência normativa indicada, sem alegar consulta jurídica.

SPRING. Documentação oficial do Spring Boot. https://docs.spring.io/spring-boot/ . Referência técnica para aprofundamento.

H2. Documentação do banco H2. https://h2database.com/html/main.html . Referência técnica para aprofundamento.

OWASP. Password Storage Cheat Sheet. https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html . Referência para revisão de configuração em produção.
