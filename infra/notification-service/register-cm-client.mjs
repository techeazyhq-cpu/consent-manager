// Registers consent-manager as a client of a local notification-service and writes its API key to
// infra/notification-service/cm-client.env, which infra/docker-compose.yml loads into cm-ntf-svc. That file
// switches cm-ntf-svc to cm.ntf.provider=notification-service; delete it to go back to the local catchers.
//
//   node infra/notification-service/register-cm-client.mjs
//
// Env: NTF_ADMIN_URL (http://localhost:18081), NTF_ADMIN_USER / NTF_ADMIN_PASSWORD (admin / admin).
// Run notification-service's own scripts/seed.mjs first (with ADMIN_URL=http://localhost:18081) so it has providers.
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ADMIN = process.env.NTF_ADMIN_URL || 'http://localhost:18081';
const CLIENT_NAME = 'consent-manager';
const ENV_FILE = join(dirname(fileURLToPath(import.meta.url)), 'cm-client.env');

async function call(method, path, token, body) {
  const res = await fetch(ADMIN + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

const { token } = await call('POST', '/api/admin/auth/login', null, {
  username: process.env.NTF_ADMIN_USER || 'admin',
  password: process.env.NTF_ADMIN_PASSWORD || 'admin',
});

const existing = (await call('GET', '/api/admin/clients', token)).find((c) => c.name === CLIENT_NAME);
if (existing) {
  console.log(`= client "${CLIENT_NAME}" already exists; its API key is only shown at creation.`);
  console.log(`  Keep the existing ${ENV_FILE}, or rotate the key in the admin UI and update that file.`);
  process.exit(0);
}

const { apiKey } = await call('POST', '/api/admin/clients', token, {
  name: CLIENT_NAME,
  allowedChannels: ['EMAIL', 'SMS', 'WHATSAPP', 'PUSH'],
});
writeFileSync(ENV_FILE, `CM_NTF_PROVIDER=notification-service\nNTF_SVC_API_KEY=${apiKey}\n`);
console.log(`+ client "${CLIENT_NAME}" created; API key written to ${ENV_FILE} (git-ignored).`);
