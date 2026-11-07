#!/usr/bin/env node
// Keeps the error dictionary honest (ADR-062).
//
//   node scripts/error-dictionary.mjs            check; exits 1 with what is wrong
//   node scripts/error-dictionary.mjs --check    the same, as CI runs it
//   node scripts/error-dictionary.mjs --write    also regenerates docs/errors/error-dictionary.md
//
// The dictionary is backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json: one entry for every API
// problem, operational log code and event the platform raises. The check reads the Java sources and holds them to it:
//   - every code the code raises is in the dictionary, with the status the dictionary gives it;
//   - every entry is raised somewhere (a code nobody raises is removed, not left to mislead);
//   - no problem is built by hand: urn:cm:problem is written in one place, Problems;
//   - the entries are well formed, and docs/errors/error-dictionary.md is what the dictionary generates.
//
// Copyright 2026 Vasantha Kumar. Licensed under the Apache License, Version 2.0.

import { existsSync, readdirSync, readFileSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DICTIONARY = join(ROOT, 'backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json');
const DOC = join(ROOT, 'docs/errors/error-dictionary.md');
const write = process.argv.includes('--write');

const SERVICES = new Set(['cm-aud-svc', 'cm-bil-svc', 'cm-cns-cmd-svc', 'cm-cns-qry-svc', 'cm-df-reg-svc', 'cm-dsr-svc',
  'cm-gw', 'cm-ntf-svc', 'cm-stf-svc', 'cm-idn-otp-spi']);
const CATEGORIES = new Set(['validation', 'authentication', 'authorization', 'subscription', 'not-found', 'conflict',
  'state', 'quota', 'rate-limit', 'dependency', 'internal', 'operations']);
const CODE = { problem: /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/, log: /^CM-[A-Z0-9]+(-[A-Z0-9]+)*$/, event: /^[a-z][a-z0-9]*(_[a-z0-9]+)*$/ };
const STATUS_CATEGORY = { 401: ['authentication'], 404: ['not-found'], 402: ['subscription'] };

const problems = [];
const fail = (msg) => problems.push(msg);

// ---------------------------------------------------------------- the dictionary
if (!existsSync(DICTIONARY)) {
  console.error(`No dictionary at ${relative(ROOT, DICTIONARY)}`);
  process.exit(1);
}
const dictionary = JSON.parse(readFileSync(DICTIONARY, 'utf8'));
const entries = dictionary.entries ?? [];
const byCode = new Map();
for (const e of entries) {
  const where = `entry ${e.code}`;
  if (byCode.has(e.code)) fail(`${where}: listed twice`);
  byCode.set(e.code, e);
  if (!['problem', 'log', 'event'].includes(e.kind)) { fail(`${where}: kind must be problem, log or event`); continue; }
  if (!CODE[e.kind].test(e.code ?? '')) fail(`${where}: a ${e.kind} code is written ${CODE[e.kind]}`);
  if (!CATEGORIES.has(e.category)) fail(`${where}: category "${e.category}" is not one of ${[...CATEGORIES].join(', ')}`);
  for (const field of ['title', 'description', 'action']) {
    if (typeof e[field] !== 'string' || e[field].trim() === '') fail(`${where}: ${field} is empty`);
  }
  if (e.title && (e.title.length > 60 || /[.]$/.test(e.title))) fail(`${where}: the title is at most 60 characters and has no full stop`);
  if (e.description && !/[.]$/.test(e.description)) fail(`${where}: the description is sentences ending in a full stop`);
  if (e.action && !/[.]$/.test(e.action)) fail(`${where}: the action is sentences ending in a full stop`);
  if (!Array.isArray(e.raisedBy) || e.raisedBy.length === 0) fail(`${where}: raisedBy names the services that raise it`);
  for (const s of e.raisedBy ?? []) if (!SERVICES.has(s)) fail(`${where}: raisedBy "${s}" is not a service`);
  if (typeof e.retryable !== 'boolean') fail(`${where}: retryable is true or false`);
  if (e.kind === 'problem') {
    if (!Number.isInteger(e.status) || e.status < 400 || e.status > 599) fail(`${where}: status must be 400 to 599`);
    const required = STATUS_CATEGORY[e.status];
    if (required && !required.includes(e.category)) fail(`${where}: status ${e.status} means category ${required.join(' or ')}`);
    if (e.status >= 500 && !['dependency', 'internal'].includes(e.category)) fail(`${where}: a 5xx is dependency or internal`);
    if (e.retryable && ![429, 503].includes(e.status)) fail(`${where}: only 429 and 503 can be retried as they are`);
  }
  if (e.kind === 'log' && !['WARN', 'ERROR'].includes(e.level)) fail(`${where}: a log code has level WARN or ERROR`);
}

// ---------------------------------------------------------------- the sources
function* javaMain(dir) {
  for (const name of readdirSync(dir)) {
    if (['build', 'node_modules', '.git', '.gradle', 'test'].includes(name)) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* javaMain(path);
    else if (name.endsWith('.java')) yield path;
  }
}

const used = new Map(); // code -> [{file, line, status?}]
const note = (code, file, line, status) => {
  if (!used.has(code)) used.set(code, []);
  used.get(code).push({ file, line, status });
};
const lineOf = (text, index) => text.slice(0, index).split('\n').length;
const PATTERNS = [
  // thrown with its status: DomRuleViolation(409, "code", ...)
  { re: /DomRuleViolation\(\s*(\d{3})\s*,\s*"([a-z0-9-]+)"/g, status: 1, code: 2 },
  // a policy's reason, passed on by the handler that throws it
  { re: /SpecResult\.violated\(\s*"([a-z0-9-]+)"/g, code: 1 },
  // answered by Problems: Problems.of("code", ...), Problems.json("code", ...), Problems.body("code", ...)
  { re: /Problems\.\w+\(\s*"([a-z0-9-]+)"/g, code: 1 },
  // the gateway's helper: problem(exchange, HttpStatus.X, "code", ...)
  { re: /problem\(\s*exchange\s*,\s*HttpStatus\.[A-Z_]+\s*,\s*"([a-z0-9-]+)"/g, code: 1 },
  // refused by a filter: new Refused(HttpStatus.X, "code", ...)
  { re: /Refused\(\s*HttpStatus\.[A-Z_]+\s*,\s*"([a-z0-9-]+)"/g, code: 1 },
  // an operational log code, written in square brackets at the start of the message
  { re: /\[(CM-[A-Z0-9]+(?:-[A-Z0-9]+)*)\]/g, code: 1 },
  // a Keycloak event
  { re: /\.error\(\s*"(cm_[a-z0-9_]+)"\s*\)/g, code: 1 },
];
const HAND_BUILT = /urn:cm:problem:/;
const handBuilt = [];

for (const file of javaMain(join(ROOT, 'backend'))) {
  const rel = relative(ROOT, file).replaceAll('\\', '/');
  const text = readFileSync(file, 'utf8');
  for (const p of PATTERNS) {
    for (const m of text.matchAll(p.re)) note(m[p.code], rel, lineOf(text, m.index), p.status ? Number(m[p.status]) : undefined);
  }
  // the URN is written once, in Problems; the dictionary's own docs and comments may name it
  if (!rel.endsWith('/webkit/Problems.java')) {
    for (const [i, line] of text.split('\n').entries()) {
      const code = line.trim();
      if (HAND_BUILT.test(line) && !code.startsWith('*') && !code.startsWith('//') && !code.startsWith('/*')) {
        handBuilt.push(`${rel}:${i + 1}`);
      }
    }
  }
}

for (const [code, sites] of used) {
  const e = byCode.get(code);
  const first = `${sites[0].file}:${sites[0].line}`;
  if (!e) {
    fail(`${code}: raised at ${first} but not in the dictionary. Add it to ${relative(ROOT, DICTIONARY)}`);
    continue;
  }
  for (const s of sites) {
    if (s.status !== undefined && e.kind === 'problem' && s.status !== e.status) {
      fail(`${code}: ${s.file}:${s.line} raises ${s.status}, the dictionary says ${e.status}. One name, one status`);
    }
  }
}
for (const e of entries) {
  if (!used.has(e.code)) fail(`${e.code}: in the dictionary but raised nowhere. Remove it, or raise it`);
}
for (const site of handBuilt) fail(`${site}: builds "urn:cm:problem:" by hand. Use Problems, so the title and code come from the dictionary`);

// ---------------------------------------------------------------- the readable catalogue
const esc = (s) => s.replaceAll('|', '\\|');
const svc = (list) => list.map((s) => s.replace(/^cm-/, '').replace(/-svc$/, '')).join(', ');
function render() {
  const out = [];
  out.push('<!-- Generated from backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json by');
  out.push('     node scripts/error-dictionary.mjs --write. Do not edit by hand: edit the dictionary. -->', '');
  out.push('# Error dictionary', '');
  out.push('Every error, warning and event this platform raises, in one place ([ADR-062](../adr/adr-062-error-dictionary.md)).',
    `${entries.length} entries: ${entries.filter((e) => e.kind === 'problem').length} API problems, `
    + `${entries.filter((e) => e.kind === 'log').length} operational log codes and `
    + `${entries.filter((e) => e.kind === 'event').length} event.`, '');
  out.push('## How an API error looks', '');
  out.push('An error response is `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):', '');
  out.push('```json', JSON.stringify({
    type: 'urn:cm:problem:quota-exceeded', code: 'quota-exceeded', title: 'Monthly allowance used up', status: 429,
    detail: 'DF 6f1c… is not eligible to raise a new consent request', retryable: true,
    correlationId: '0b1e7c7a-6f43-4f4e-9d52-8d2a1f4c7a10',
  }, null, 2), '```', '');
  out.push('- **`code`** (also the end of `type`) is the stable name: write your logic against it, never against `title` or `detail`.');
  out.push('- **`title`** is the same short, human sentence every time for a code, from this dictionary.');
  out.push('- **`detail`** says what was wrong this time (which field, which id). It can change; do not parse it.');
  out.push('- **`retryable`** says whether trying the same request again later can work. Honour `Retry-After` when present.');
  out.push('- **`correlationId`** is also the `X-Correlation-Id` header. Quote it when you report a problem.', '');
  out.push('## Naming', '');
  out.push('- API problem codes are lower-case words joined by hyphens, and never change once released: `quota-exceeded`.');
  out.push('- Operational log codes are `CM-` and upper-case words, written in square brackets at the start of the message so an '
    + 'alert can find them: `[CM-ERASURE-STUCK]`.');
  out.push('- Events are lower-case words joined by underscores, as Keycloak names its own: `cm_otp_rate_limited`.');
  out.push('- A code has one status. If two situations need different statuses, they are two codes.', '');
  out.push('## Adding one', '');
  out.push('Add the entry to the dictionary and raise it from the code (`new DomRuleViolation(status, "code", detail)`, '
    + '`Problems.body("code", ...)`, or `[CM-...]` in a log message); then run `node scripts/error-dictionary.mjs --write`. '
    + 'CI refuses a code that is raised but not listed, a listed code nobody raises, a status that differs from the '
    + 'dictionary\'s, a problem built by hand, and a stale copy of this page.', '');

  const problemsByCategory = new Map();
  for (const e of entries.filter((x) => x.kind === 'problem')) {
    if (!problemsByCategory.has(e.category)) problemsByCategory.set(e.category, []);
    problemsByCategory.get(e.category).push(e);
  }
  out.push('## API problems', '');
  for (const category of [...CATEGORIES].filter((c) => problemsByCategory.has(c))) {
    out.push(`### ${category[0].toUpperCase()}${category.slice(1).replace('-', ' ')}`, '');
    out.push('| Code | Status | Title | What it means | What to do | Retry | Raised by |', '|---|---|---|---|---|---|---|');
    for (const e of problemsByCategory.get(category).sort((a, b) => a.code.localeCompare(b.code))) {
      out.push(`| \`${e.code}\` | ${e.status} | ${esc(e.title)} | ${esc(e.description)} | ${esc(e.action)} | ${e.retryable ? 'yes' : 'no'} | ${svc(e.raisedBy)} |`);
    }
    out.push('');
  }
  for (const [kind, heading, intro] of [
    ['log', 'Operational log codes', 'Written as `[CODE]` at the start of a WARN or ERROR message, for alerts and runbooks.'],
    ['event', 'Events', 'Raised as Keycloak events.']]) {
    const list = entries.filter((e) => e.kind === kind).sort((a, b) => a.code.localeCompare(b.code));
    out.push(`## ${heading}`, '', intro, '');
    out.push(kind === 'log' ? '| Code | Level | Title | What it means | What to do | Raised by |' : '| Code | Title | What it means | What to do | Raised by |',
      kind === 'log' ? '|---|---|---|---|---|---|' : '|---|---|---|---|---|');
    for (const e of list) {
      out.push(kind === 'log'
        ? `| \`${e.code}\` | ${e.level} | ${esc(e.title)} | ${esc(e.description)} | ${esc(e.action)} | ${svc(e.raisedBy)} |`
        : `| \`${e.code}\` | ${esc(e.title)} | ${esc(e.description)} | ${esc(e.action)} | ${svc(e.raisedBy)} |`);
    }
    out.push('');
  }
  return out.join('\n');
}

const rendered = render();
if (write) {
  mkdirSync(dirname(DOC), { recursive: true });
  writeFileSync(DOC, rendered);
} else if (!existsSync(DOC) || readFileSync(DOC, 'utf8').replaceAll('\r\n', '\n') !== rendered) {
  fail(`${relative(ROOT, DOC)} is out of date. Run: node scripts/error-dictionary.mjs --write`);
}

if (problems.length > 0) {
  console.error(`${problems.length} problem(s) with the error dictionary:`);
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}
console.log(`The error dictionary holds: ${entries.length} entries, all raised, all well formed${write ? ', page regenerated' : ''}.`);
