"""Gera coleção Postman v2.1 e plano de testes a partir dos mesmos cenários."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
items=[]
plan=['# Plano de testes executável', '', 'Executar na ordem indicada com banco criado pelo seed. '
      'Cada cenário tem asserções na coleção. CLIENTE é criado em cada execução; adminPassword '
      'é configurado localmente e não deve ser exportado. Unidade 1 e produto 1 vêm do seed.', '',
      '| ID | Requisição e entrada | Pré-condição | Saída esperada |', '|---|---|---|---|']

def add(name,method,path,status,body=None,role='client',key=None,checks=None,precondition='Fluxo anterior concluído'):
    headers=[]
    if role:
        headers.append({'key':'Authorization','value':'Bearer {{'+('adminToken' if role=='admin' else 'token')+'}}'})
    if body is not None: headers.append({'key':'Content-Type','value':'application/json'})
    if key: headers.append({'key':'Idempotency-Key','value':key})
    tests=[f'pm.test("HTTP {status}", () => pm.response.to.have.status({status}));']
    if status!=204: tests.append('const j = pm.response.json();')
    tests += checks or []
    request={'method':method,'header':headers,'url':'{{baseUrl}}'+path,
             'description':precondition+'; resultado esperado HTTP '+str(status)}
    if body is not None:request['body']={'mode':'raw','raw':json.dumps(body,ensure_ascii=False,indent=2),'options':{'raw':{'language':'json'}}}
    items.append({'name':name,'request':request,'event':[{'listen':'test','script':{'type':'text/javascript','exec':tests}}]})
    payload=json.dumps(body,ensure_ascii=False) if body else 'Sem corpo'
    plan.append(f'| {name} | `{method} {path}`; `{payload}` | {precondition} | HTTP {status}; asserções na requisição homônima |')

add('T01 Cadastro de cliente','POST','/usuarios',201,{'nome':'Cliente de teste','email':'{{clientEmail}}','senha':'{{clientPassword}}'},role=None,
    checks=['pm.test("perfil CLIENTE", () => pm.expect(j.perfil).to.eql("CLIENTE"));'],precondition='API iniciada; email novo gerado automaticamente')
add('T02 Login cliente','POST','/auth/login',200,{'email':'{{clientEmail}}','senha':'{{clientPassword}}'},role=None,
    checks=['pm.test("JWT emitido", () => pm.expect(j.accessToken).to.be.a("string"));','pm.collectionVariables.set("token", j.accessToken);'])
add('T03 Login administrador','POST','/auth/login',200,{'email':'admin@raizes.local','senha':'{{adminPassword}}'},role=None,
    checks=['pm.test("perfil ADMIN", () => pm.expect(j.user.perfil).to.eql("ADMIN"));','pm.collectionVariables.set("adminToken", j.accessToken);'],precondition='Seed aplicado; variável adminPassword informada localmente')
add('T04 Cardápio por unidade','GET','/unidades/1/cardapio',200,role=None,
    checks=['pm.test("produto e preço", () => {pm.expect(j.items[0].produtoId).to.eql(1);pm.expect(j.items[0].precoCentavos).to.eql(2500);});'])
add('T05 Consentimento fidelidade','PUT','/fidelidade/consentimento',200,{'consentimento':True},
    checks=['pm.test("consentimento registrado", () => pm.expect(j.consentimento).to.eql(true));'])
pedido={'unidadeId':1,'canalPedido':'TOTEM','itens':[{'produtoId':1,'quantidade':2}],'formaPagamento':'MOCK'}
add('T06 Criar pedido multicanal','POST','/pedidos',201,pedido,key='{{runId}}-pedido',
    checks=['pm.test("pedido aguarda pagamento", () => {pm.expect(j.status).to.eql("AGUARDANDO_PAGAMENTO");pm.expect(j.totalCentavos).to.eql(5000);pm.expect(j.canalPedido).to.eql("TOTEM");});','pm.collectionVariables.set("pedidoId", j.id);'])
add('T07 Idempotência pedido','POST','/pedidos',201,pedido,key='{{runId}}-pedido',
    checks=['pm.test("mesmo pedido", () => pm.expect(j.id).to.eql(Number(pm.collectionVariables.get("pedidoId"))));'])
add('T08 Pagamento negado','POST','/pedidos/{{pedidoId}}/pagamentos',201,{'cenario':'NEGAR'},key='{{runId}}-negado',
    checks=['pm.test("gateway negou sem aprovar pedido", () => pm.expect(j.status).to.eql("NEGADO"));'])
add('T09 Pagamento aprovado em nova tentativa','POST','/pedidos/{{pedidoId}}/pagamentos',201,{'cenario':'APROVAR'},key='{{runId}}-aprovado',
    checks=['pm.test("gateway aprovou", () => pm.expect(j.status).to.eql("APROVADO"));'])
add('T10 Cozinha concluiu preparo','PATCH','/pedidos/{{pedidoId}}/status',200,{'status':'PRONTO'},role='admin',
    checks=['pm.test("pronto", () => pm.expect(j.status).to.eql("PRONTO"));'])
add('T11 Entrega e fidelização','PATCH','/pedidos/{{pedidoId}}/status',200,{'status':'ENTREGUE'},role='admin',
    checks=['pm.test("entregue", () => pm.expect(j.status).to.eql("ENTREGUE"));'])
add('T12 Saldo fidelidade','GET','/fidelidade',200,
    checks=['pm.test("50 pontos creditados", () => pm.expect(j.saldo).to.eql(50));'])
add('T13 Filtro canalPedido','GET','/pedidos?canalPedido=TOTEM&status=ENTREGUE&page=1&limit=20',200,
    checks=['pm.test("pedido filtrado", () => pm.expect(j.items.some(x => x.id === Number(pm.collectionVariables.get("pedidoId")) && x.canalPedido === "TOTEM")).to.eql(true));'])
err=['pm.test("erro padronizado", () => pm.expect(j).to.have.all.keys("error","message","details","timestamp","path","requestId"));']
add('T14 Sem autenticação','GET','/pedidos',401,role=None,checks=err,precondition='Omitir Authorization')
add('T15 Perfil sem permissão','POST','/unidades',403,{'nome':'Unidade teste','endereco':'Rua teste, 123'},checks=err)
add('T16 Canal obrigatório ausente','POST','/pedidos',422,{k:v for k,v in pedido.items() if k!='canalPedido'},key='{{runId}}-sem-canal',checks=err)
add('T17 Canal inválido','POST','/pedidos',422,{**pedido,'canalPedido':'OUTRO'},key='{{runId}}-canal-invalido',checks=err)
add('T18 Estoque insuficiente','POST','/pedidos',409,{**pedido,'itens':[{'produtoId':1,'quantidade':1000}]},key='{{runId}}-sem-estoque',checks=err,
    precondition='Seed com saldo de 100; não adicionar estoque antes deste teste')
add('T19 Pedido inexistente','GET','/pedidos/999999999',404,checks=err)
add('T20 Transição inválida','PATCH','/pedidos/{{pedidoId}}/status',409,{'status':'PRONTO'},role='admin',checks=err)
collection={'info':{'name':'Raízes do Nordeste — fluxo e erros','schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    'description':'Execute em ordem. Configure adminPassword em ambiente local. Dados são fictícios; pagamento exclusivamente mock.'},
    'variable':[{'key':k,'value':v} for k,v in [('baseUrl','http://127.0.0.1:8000'),('adminPassword',''),('clientPassword','ClienteEstudo123!')]],
    'event':[{'listen':'prerequest','script':{'type':'text/javascript','exec':[
    'if (pm.info.requestName.startsWith("T01 ")) {',
    '  const id = pm.variables.replaceIn("{{$guid}}");',
    '  pm.collectionVariables.set("runId", id);',
    '  pm.collectionVariables.set("clientEmail", "cliente-" + id + "@example.com");',
    '}']}}], 'item':items}
(ROOT/'docs/postman_collection.json').write_text(json.dumps(collection,ensure_ascii=False,indent=2)+'\n')
(ROOT/'docs/plano-testes.md').write_text('\n'.join(plan)+'\n')
print('Coleção gerada: 20 cenários com asserções.')
