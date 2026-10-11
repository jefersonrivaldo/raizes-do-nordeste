// Executor opcional. Instale newman fora do checkout ou como ferramenta global.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
let newman;
try { newman = require('newman'); }
catch (_) { throw new Error('Instale Newman e defina NODE_PATH para seu diretório node_modules. Veja README.'); }
const local = {};
const envPath = path.join(root, '.env');
if (fs.existsSync(envPath)) {
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const found = line.match(/^([A-Z_]+)=(.*)$/);
    if (found) local[found[1]] = found[2];
  }
}
const password = process.env.SEED_ADMIN_PASSWORD || local.SEED_ADMIN_PASSWORD;
if (!password) throw new Error('SEED_ADMIN_PASSWORD ausente. Configure localmente; não passe senha na linha de comando.');
newman.run({collection: JSON.parse(fs.readFileSync(path.join(root, 'docs/postman_collection.json'))),
    envVar: [{key:'adminPassword',value:password}, {key:'baseUrl',value:process.env.API_BASE_URL || 'http://127.0.0.1:8000'}],
    reporters: ['cli'], reporter: {cli:{noBanner:true,noSummary:true,noAssertions:true,noConsole:true}}}, (err, summary) => {
  if (err) { console.error('Não foi possível executar a coleção.'); process.exitCode=1; return; }
  const result = {executadoEm:new Date().toISOString(), ferramenta:'Newman 6.2.1',
    requisicoes:summary.run.stats.requests, assercoes:summary.run.stats.assertions,
    falhas:summary.run.failures.map(f=>({teste:f.source?.name || 'sem nome', mensagem:f.error?.message || 'falha'}))};
  fs.writeFileSync(path.join(root, 'docs/evidencias-postman.json'), JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({requisicoes:result.requisicoes, assercoes:result.assercoes, falhas:result.falhas.length}));
  if (result.falhas.length) process.exitCode=1;
});
