"""Exporta contratos e diagramas a partir da API e do schema real do banco."""
import json
import os
import re
import subprocess
from pathlib import Path
from app.api.main import app
from app.infrastructure.database import transaction
ROOT=Path(__file__).resolve().parents[1]
DOCS=ROOT/'docs'
schema=app.openapi()
from app.api.examples import EXAMPLES as examples
for name,example in examples.items():
 if name in schema['components']['schemas']:
  schema['components']['schemas'][name]['example']=example

def sample(s):
 if '$ref' in s:
  name=s['$ref'].split('/')[-1]
  return examples.get(name,sample(schema['components']['schemas'][name])) if name not in examples else examples[name]
 if 'example' in s:return s['example']
 if 'enum' in s:return s['enum'][0]
 if 'default' in s:return s['default']
 if 'anyOf' in s:return sample(s['anyOf'][0])
 t=s.get('type')
 if t=='object':return {k:sample(v) for k,v in s.get('properties',{}).items()}
 if t=='array':return [sample(s['items'])]
 if t=='integer':return 1
 if t=='boolean':return True
 return 'texto'

def block(value):return '```json\n'+json.dumps(value,ensure_ascii=False,indent=2)+'\n```'
content=['# Contratos da API', '', 'Documento gerado a partir das rotas e schemas reais. Exemplos são ilustrativos, '
         'não registros de produção. HTTP 201 no pagamento significa tentativa persistida; examine status '
         'APROVADO/NEGADO/ERRO no corpo. Centavos são inteiros. Campos extras são rejeitados.', '',
         'Nas listagens, page inicia em 1 e limit varia de 1 a 100 (padrão 20). Idempotency-Key '
         'tem 8 a 100 caracteres alfanuméricos, ponto, hífen ou sublinhado. Reenvios com mesmo corpo/chave '
         'devolvem o recurso no estado atual, sem repetir efeitos. Equipe de atendimento informa clienteId na criação.', '']
for path,methods in schema['paths'].items():
 for method,op in methods.items():
  if method not in ('get','post','put','patch','delete'):continue
  content += [f'## {method.upper()} {path}', '',op['summary'], '',
    'Autenticação: '+ ('JWT Bearer; permissões conforme finalidade acima.' if op.get('security') else 'Público.'),'',
    'Parâmetros:', '']
  pars=op.get('parameters',[])
  content += ['| Nome | Local | Obrigatório | Contrato |','|---|---|---|---|']
  for p in pars:content.append(f"| {p['name']} | {p['in']} | {'sim' if p.get('required') else 'não'} | `{json.dumps(p['schema'],ensure_ascii=False)}` |")
  if not pars:content.append('| — | — | — | Sem parâmetros |')
  body=op.get('requestBody',{}).get('content',{}).get('application/json',{}).get('schema')
  content += ['', 'Request:', '',block(sample(body)) if body else 'Sem corpo.','']
  for status,response in op['responses'].items():
   if status.startswith('2'):
    shape=response.get('content',{}).get('application/json',{}).get('schema')
    if shape:
     example=sample(shape)
     response['content']['application/json']['example']=example
     content += [f'Response HTTP {status}:','',block(example),'']
    else:content += [f'Response HTTP {status}: sem corpo.','']
  content += ['Status documentados: '+', '.join(op['responses'])+'.', '',
              'Erro: schema ErrorResponse; 401 credencial, 403 autorização, 404 recurso, 409 regra, '
              '422 validação, 503 indisponibilidade de banco. Nem todos os erros se aplicam a toda rota.', '']
content += ['## Padrão de erro', '',block(examples['ErrorResponse']), '',
            'Para validação, details identifica o campo e o problema, sem repetir dados enviados. '
            'Exemplo: canalPedido omitido gera HTTP 422 e error=VALIDACAO.', '']
(DOCS/'api.md').write_text('\n'.join(content))
(DOCS/'openapi.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n')
# Mesmos exemplos ficam disponíveis no Swagger em execução.
app.openapi_schema=schema

out=DOCS/'diagramas'
def render(name,dot):
 (out/(name+'.dot')).write_text(dot)
 for fmt in ['svg','png']:
  subprocess.run(['dot','-T'+fmt,str(out/(name+'.dot')),'-o',str(out/(name+'.'+fmt))],check=True, env={**os.environ, 'XDG_CACHE_HOME': '/tmp/raizes-font-cache'})

with transaction() as db:
 tables=[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
 nodes=[];edges=[]
 for table in tables:
  cols=db.execute(f'PRAGMA table_info({table})').fetchall()
  foreign=db.execute(f'PRAGMA foreign_key_list({table})').fetchall()
  fks={f['from'] for f in foreign}
  attrs=[]
  for col in cols:
   marks=(' PK' if col['pk'] else '')+(' FK' if col['name'] in fks else '')+(' NOT NULL' if col['notnull'] else '')
   attrs.append(col['name']+' : '+col['type']+marks)
  label='{'+table+'|'+'\\l'.join(attrs)+'\\l}'
  nodes.append(f'{table} [label="{label}"];')
  for f in foreign:
   edges.append(f'{table} -> {f["table"]} [label={json.dumps(f["from"]+" → "+f["to"]+" (N:1)", ensure_ascii=False)}];')
 render('der','digraph ER { graph [rankdir=TB, bgcolor="white", nodesep=0.5, ranksep=0.8]; node [shape=record,fontname="DejaVu Sans",fontsize=10]; edge [fontname="DejaVu Sans",fontsize=9];'+ '\n'.join(nodes+edges)+'}')
render('casos-de-uso','''digraph UC {
 graph [rankdir=LR,bgcolor="white"]; node [fontname="DejaVu Sans",fontsize=12];
 cliente [shape=box,label="Cliente\\nAPP / WEB / TOTEM / PICKUP"];
 atendente [shape=box,label="Atendente / BALCAO"];
 cozinha [shape=box,label="Cozinha"];
 gerente [shape=box,label="Gerente / Administrador"];
 gateway [shape=box,label="Gateway externo simulado"];
 subgraph cluster_api {label="API Raízes do Nordeste";
 node [shape=ellipse]; cadastro [label="Cadastrar e autenticar"];
 cardapio [label="Consultar cardápio por unidade"];
 pedido [label="Realizar pedido multicanal"];
 pagar [label="Solicitar pagamento mock"];
 status [label="Atualizar status / cancelar"];
 estoque [label="Gerenciar estoque por unidade"];
 fidelidade [label="Consentir / consultar / resgatar pontos"];
 gestao [label="Gerenciar unidades, produtos e perfis"];
 privacidade [label="Desidentificar conta própria"];
 auditoria [label="Consultar auditoria (ADMIN)"];
 }
 cliente -> cadastro;cliente -> cardapio;cliente -> pedido;cliente -> pagar;cliente -> fidelidade;cliente -> privacidade;cliente -> status;
 atendente -> pedido;atendente -> status;atendente -> pagar;cozinha -> status;
 gerente -> estoque;gerente -> gestao;gerente -> status;gerente -> auditoria;
 pagar -> gateway [dir=both];pedido -> estoque [style=dashed,label="valida e baixa"];
 pedido -> fidelidade [style=dashed,label="resgate opcional"];
}''')
render('classes','''digraph Classes {
 graph [rankdir=LR,bgcolor="white"];node [shape=record,fontname="DejaVu Sans",fontsize=11];edge [fontname="DejaVu Sans",fontsize=10];
 Usuario [label="{Usuario|id; nome; email; hashSenha\\lperfil; unidadeId; ativo\\lconsentimento; pontos\\l|consentir(); desidentificar()}"];
 Unidade [label="{Unidade|id; nome; endereco; ativo}"];
 Produto [label="{Produto|id; nome; descricao; ativo}"];
 Estoque [label="{Estoque|unidadeId; produtoId\\lquantidade; precoCentavos\\l|movimentar()}"];
 Pedido [label="{Pedido|id; clienteId; unidadeId; canalPedido\\lstatus; totalCentavos; pontosResgate\\l|criar(); transicionar(); cancelar()}"];
 Item [label="{ItemPedido|produtoId; nomeHistorico\\lquantidade; precoUnitarioCentavos}"];
 Pagamento [label="{Pagamento|pedidoId; status; valorCentavos\\lpayloadGateway\\l|solicitarMock()}"];
 Movimento [label="{MovimentoFidelidade|usuarioId; pedidoId; quantidade; motivo}"];
 Usuario -> Pedido [label="1 : 0..N"];Unidade -> Pedido [label="1 : 0..N"];
 Unidade -> Estoque [label="1 : 0..N"];Produto -> Estoque [label="1 : 0..N"];
 Pedido -> Item [label="1 : 1..N"];Produto -> Item [label="1 : 0..N"];
 Pedido -> Pagamento [label="1 : 0..N"];Usuario -> Movimento [label="1 : 0..N"];
}''')
render('arquitetura','''digraph Arquitetura {
 graph [rankdir=TB,bgcolor="white"]; node [shape=box,fontname="DejaVu Sans",fontsize=12,style="rounded,filled",fillcolor="#f0f7ed"];
 canais [label="APP / TOTEM / BALCAO / PICKUP / WEB"];
 api [label="API: FastAPI / Pydantic\\nRotas, JWT, perfis e contratos"];
 application [label="Application: serviços de casos de uso\\nPedidos, pagamento, status e fidelidade"];
 domain [label="Domain: regras puras\\nCanal, perfil, status, transições, descontos"];
 infra [label="Infrastructure\\nSQLite, migrations, hash, JWT, gateway mock"];
 db [shape=cylinder,label="SQLite persistente"];
 gateway [label="Gateway MOCK (sem rede externa)"];
 canais -> api;api -> application;application -> domain;application -> infra;infra -> db;infra -> gateway;
}''')
print('OpenAPI, contratos e quatro diagramas gerados.')
