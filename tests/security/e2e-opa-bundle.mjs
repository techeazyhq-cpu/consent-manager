#!/usr/bin/env node
/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

// The authorisation policy bundle, end to end with real OPA servers (ADR-068): builds a signed bundle with scripts/opa-bundle.sh,
// serves it from a throwaway web server, and starts OPA pulling it with signature verification, as a deployment does:
//   - with the right public key, OPA loads it, becomes ready, and evaluates the policy (a request without a token is refused)
//   - with a bundle altered after it was signed (its policy changed to allow everything), OPA refuses it and has no policy
//   - with a different public key, OPA refuses it
//   - the bundle contains the policy and not its tests, and says which revision of the source it was built from
// Needs Docker and openssl. It leaves nothing running.
//
//   node tests/security/e2e-opa-bundle.mjs

import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..', '..');
const OUT = 'dist/opa-e2e';
const IMAGE = readFileSync(join(ROOT, 'infra/docker-compose.yml'), 'utf8').match(/openpolicyagent\/opa:[0-9.]+/)[0];
const WORK = join(ROOT, OUT);
const KEY_ID = 'cm-policy';
let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { stdio: 'pipe', cwd: ROOT, ...opts }).toString();
const docker = (...args) => run('docker', args, { env: { ...process.env, MSYS_NO_PATHCONV: '1' } });

rmSync(WORK, { recursive: true, force: true });
mkdirSync(WORK, { recursive: true });
const keys = join(WORK, 'keys');
mkdirSync(keys);
run('openssl', ['genrsa', '-out', join(keys, 'private.pem'), '2048']);
run('openssl', ['genrsa', '-out', join(keys, 'other-private.pem'), '2048']);
run('openssl', ['rsa', '-in', join(keys, 'other-private.pem'), '-pubout', '-out', join(keys, 'other-public.pem')]);

// the bundle, signed
const built = run('bash', ['scripts/opa-bundle.sh', '--out', OUT, '--version', 'e2e'], { env: { ...process.env, OPA_SIGNING_KEY: join(keys, 'private.pem'), OPA_SIGNING_KEY_ID: KEY_ID } });
const manifest = JSON.parse(readFileSync(join(WORK, 'manifest-e2e.json'), 'utf8'));
expect(manifest.signed === true && manifest.keyId === KEY_ID && /^[0-9a-f]{64}$/.test(manifest.sha256), 'the build signs the bundle and records its checksum', `${manifest.file}`);
expect(/PASS: \d+\/\d+/.test(built), 'after the policy tests pass', built.match(/PASS: \d+\/\d+/)?.[0]);
expect(/^[0-9a-f]{40}$/.test(manifest.revision), 'and says which revision of the source it was built from', manifest.revision.slice(0, 8));
const listing = run('tar', ['-tzf', `${OUT}/${manifest.file}`]).split('\n');
expect(listing.some((f) => f.endsWith('cns_authz.rego')) && !listing.some((f) => f.endsWith('_test.rego')), 'it holds the policy and not its tests');
expect(listing.some((f) => f.endsWith('.signatures.json')), 'and its signature');
const publicKey = readFileSync(join(WORK, `cm-authz-e2e.public.pem`), 'utf8');

// a tampered copy: the policy altered after signing
const tampered = join(WORK, 'tampered');
mkdirSync(tampered);
run('tar', ['-xzf', `${OUT}/${manifest.file}`, '-C', `${OUT}/tampered`]);
let altered = 0;
const walk = (dir) => readdirSync(dir, { withFileTypes: true }).forEach((e) => {
  const p = join(dir, e.name);
  if (e.isDirectory()) walk(p);
  else if (e.name.endsWith('cns_authz.rego')) {
    writeFileSync(p, readFileSync(p, 'utf8').replace('default allow := false', 'default allow := true'));
    altered++;
  }
});
walk(tampered);
run('tar', ['-czf', `${OUT}/tampered.tar.gz`, '-C', `${OUT}/tampered`, '.']);
expect(altered === 1, 'a copy is made with the policy changed to allow everything, signature untouched');

// serve the bundles
const server = createServer((req, res) => {
  const file = req.url.split('?')[0].replace(/^\/+/, '');
  try {
    const body = readFileSync(join(WORK, file === 'good.tar.gz' ? manifest.file : file === 'bad.tar.gz' ? 'tampered.tar.gz' : 'missing'));
    res.writeHead(200, { 'Content-Type': 'application/gzip' }).end(body);
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise((r) => server.listen(0, '0.0.0.0', r));
const port = server.address().port;

const started = [];
async function opa(name, resource, publicPem, hostPort) {
  const config = `services:
  bundles:
    url: http://host.docker.internal:${port}
bundles:
  authz:
    service: bundles
    resource: ${resource}
    polling: {min_delay_seconds: 1, max_delay_seconds: 2}
    signing:
      keyid: ${KEY_ID}
keys:
  ${KEY_ID}:
    algorithm: RS256
    key: |
${publicPem.split('\n').map((l) => `      ${l}`).join('\n')}
`;
  const cfg = join(WORK, `${name}.yaml`);
  writeFileSync(cfg, config);
  docker('rm', '-f', name);
  docker('run', '-d', '--name', name, '--add-host', 'host.docker.internal:host-gateway', '-p', `${hostPort}:8181`, '-v', `${cfg}:/config.yaml:ro`,
    IMAGE, 'run', '--server', '--addr=0.0.0.0:8181', '--config-file=/config.yaml');
  started.push(name);
  return `http://localhost:${hostPort}`;
}
const ready = async (base) => {
  for (let i = 0; i < 20; i++) {
    try {
      if ((await fetch(`${base}/health?bundles`)).status === 200) return true;
    } catch { /* not up yet */ }
    await sleep(1000);
  }
  return false;
};
const decide = async (base) => (await (await fetch(`${base}/v1/data/consent/authorization/allow`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ input: {} }) })).json());

try {
  const good = await opa('cm-opa-e2e-good', 'good.tar.gz', publicKey, 18181);
  expect(await ready(good), 'OPA with the right public key loads the signed bundle and is ready');
  const answer = await decide(good);
  expect(answer.result === false, 'and evaluates the policy: a request with no token is not allowed', JSON.stringify(answer));

  const bad = await opa('cm-opa-e2e-bad', 'bad.tar.gz', publicKey, 18182);
  expect(!(await ready(bad)), 'OPA refuses a bundle altered after it was signed: it never becomes ready');
  const none = await decide(bad);
  expect(none.result === undefined, 'and holds no policy, so the altered "allow everything" is never in force', JSON.stringify(none));

  const wrong = await opa('cm-opa-e2e-wrongkey', 'good.tar.gz', readFileSync(join(keys, 'other-public.pem'), 'utf8'), 18183);
  expect(!(await ready(wrong)), 'OPA refuses a bundle whose signature is not by the key it trusts');
} finally {
  for (const name of started) {
    try {
      docker('rm', '-f', name);
    } catch { /* already gone */ }
  }
  server.close();
  rmSync(keys, { recursive: true, force: true });
}

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
