// #570 — o passo "Auto-reply to new issues" (job `respond` do kof-issues-agent.yml) roda dentro do
// actions/github-script: `github` = cliente Octokit (SEM `.event`), `context` = contexto do workflow.
// Este harness extrai o script REAL do YAML e o executa com esse shape, sem rede.
'use strict';
const fs = require('fs');
const path = require('path');

// KOF_ISSUES_AGENT_YML aponta outro arquivo (ex.: o workflow de ANTES do fix, p/ provar o RED).
const yamlPath = process.env.KOF_ISSUES_AGENT_YML
  || path.join(__dirname, '..', '..', '.github', 'workflows', 'kof-issues-agent.yml');
const lines = fs.readFileSync(yamlPath, 'utf8').split(/\r?\n/);

function extractScript(stepName) {
  const start = lines.findIndex((l) => l.includes('name: ' + stepName));
  if (start < 0) throw new Error('step nao encontrado: ' + stepName);
  const at = lines.findIndex((l, i) => i > start && /^\s+script: \|\s*$/.test(l));
  if (at < 0) throw new Error('script: | nao encontrado apos o step');
  const first = lines.slice(at + 1).find((l) => l.trim() !== '');
  const indent = first.match(/^\s*/)[0].length;
  const body = [];
  for (let i = at + 1; i < lines.length; i++) {
    const l = lines[i];
    if (l.trim() === '') { body.push(''); continue; }
    if (l.match(/^\s*/)[0].length < indent) break;
    body.push(l.slice(indent));
  }
  return body.join('\n');
}

const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const script = extractScript('Auto-reply to new issues');
let failed = 0;

async function scenario(label, payload, check) {
  const calls = [];
  // shape real do github-script: Octokit client (rest.*) — nenhuma propriedade `event`
  const github = { rest: { issues: { createComment: async (a) => { calls.push(a); } } } };
  const context = { repo: { owner: 'KofLang', repo: 'Kof4j' }, payload };
  let err = null;
  try { await new AsyncFunction('github', 'context', script)(github, context); } catch (e) { err = e; }
  const problem = check(err, calls);
  console.log(problem ? '!!! FAIL — ' + label + ': ' + problem : '  ok  — ' + label);
  if (problem) failed++;
}

(async () => {
  const human = (login) => ({ login, type: 'User' });
  // comentario HUMANO de quem NAO abriu a issue: nao pode lancar (bug #570) nem comentar
  await scenario('comentario humano de OUTRA pessoa nao lanca excecao e nao comenta',
    { action: 'created', comment: { user: human('bob') }, issue: { number: 7, user: human('alice'), comments: 1 } },
    (err, calls) => err ? String(err.message) : (calls.length ? 'postou comentario indevido' : null));
  await scenario('comentario de BOT retorna cedo e nao comenta',
    { action: 'created', comment: { user: { login: 'kof-agent-worker', type: 'Bot' } }, issue: { number: 7, user: human('alice'), comments: 1 } },
    (err, calls) => err ? String(err.message) : (calls.length ? 'bot recebeu resposta' : null));
  // comportamento vivo do fix: agradece o AUTOR no PRIMEIRO comentario, UMA vez, na issue certa
  const oneOn = (n) => (err, calls) => err ? String(err.message)
    : (calls.length === 1 && calls[0].issue_number === n && calls[0].owner === 'KofLang' ? null
      : 'esperado 1 comentario na #' + n + ', veio ' + JSON.stringify(calls.map((c) => c.issue_number)));
  await scenario('1o comentario do AUTOR (comments=1) comenta UMA vez na issue certa',
    { action: 'created', comment: { user: human('alice') }, issue: { number: 42, user: human('alice'), comments: 1 } }, oneOn(42));
  await scenario('comentario POSTERIOR do autor (comments=3) nao comenta de novo',
    { action: 'created', comment: { user: human('alice') }, issue: { number: 42, user: human('alice'), comments: 3 } },
    (err, calls) => err ? String(err.message) : (calls.length ? 'agradeceu de novo' : null));
  await scenario('sem issue.comments no payload conta como 1o comentario',
    { action: 'created', comment: { user: human('alice') }, issue: { number: 9, user: human('alice') } }, oneOn(9));
  // #600 — job `triage`: rotulagem por PALAVRA INTEIRA (sem falsos por substring)
  const labelScript = extractScript('Auto-label by content');
  const assignScript = extractScript('Assign to maintainer if security');

  async function labelScenario(label, body, check) {
    const calls = [];
    const github = { rest: { issues: { addLabels: async (a) => { calls.push(a); } } } };
    const context = { repo: { owner: 'KofLang', repo: 'Kof4j' }, payload: { issue: { number: 3, body } } };
    let err = null;
    try { await new AsyncFunction('github', 'context', labelScript)(github, context); } catch (e) { err = e; }
    const labels = calls.length ? calls[0].labels : [];
    const problem = check(err, labels);
    console.log(problem ? '!!! FAIL — ' + label + ': ' + problem : '  ok  — ' + label);
    if (problem) failed++;
  }

  await labelScenario('"source"/"enforce"/"resource" NAO produzem security',
    'the source of this; we enforce it; the resource is reused',
    (err, labels) => err ? String(err.message)
      : (labels.includes('security') ? 'security falso: ' + JSON.stringify(labels) : null));
  await labelScenario('um path JSON NAO produz js',
    'see src/main/Foo.json and the config',
    (err, labels) => err ? String(err.message)
      : (labels.includes('js') ? 'js falso: ' + JSON.stringify(labels) : null));
  await labelScenario('"docker"/"docs/..." NAO produzem documentation por substring',
    'the docker image and the docs/ folder',
    (err, labels) => err ? String(err.message)
      : (labels.includes('documentation') ? 'documentation falso: ' + JSON.stringify(labels) : null));
  await labelScenario('relato REAL de seguranca AINDA produz security',
    'security vulnerability: RCE allows arbitrary code execution',
    (err, labels) => err ? String(err.message)
      : (labels.includes('security') ? null : 'nao marcou security: ' + JSON.stringify(labels)));

  async function assignScenario(label, body, response, check) {
    const calls = [];
    const failures = [];
    const github = { rest: { issues: { addAssignees: async (a) => { calls.push(a); return response; } } } };
    const core = { setFailed: (m) => { failures.push(m); } };
    const context = { repo: { owner: 'KofLang', repo: 'Kof4j' }, payload: { issue: { number: 3, body } } };
    let err = null;
    try { await new AsyncFunction('github', 'context', 'core', assignScript)(github, context, core); } catch (e) { err = e; }
    const problem = check(err, calls, failures);
    console.log(problem ? '!!! FAIL — ' + label + ': ' + problem : '  ok  — ' + label);
    if (problem) failed++;
  }

  await assignScenario('seguranca atribui uma conta ATRIBUIVEL (melmonfre)',
    'security vulnerability', { data: { assignees: [{ login: 'melmonfre' }] } },
    (err, calls, fails) => err ? String(err.message)
      : ((calls.length === 1 && calls[0].assignees[0] === 'melmonfre' && !fails.length) ? null
        : 'assignees=' + JSON.stringify(calls.map((c) => c.assignees)) + ' fails=' + fails.length));
  await assignScenario('assignee dropado FALHA ALTO (R6, nunca silencioso)',
    'security vulnerability', { data: { assignees: [] } },
    (err, calls, fails) => err ? String(err.message)
      : (fails.length === 1 ? null : 'nao falhou alto (silencio proibido)'));
  await assignScenario('sem seguranca nao atribui',
    'a plain bug report', { data: { assignees: [] } },
    (err, calls) => err ? String(err.message) : (calls.length ? 'atribuiu indevidamente' : null));

  console.log(failed ? '== kof-issues-agent-script: VERMELHO (' + failed + ')' : '== kof-issues-agent-script: VERDE');
  process.exit(failed ? 1 : 0);
})();
