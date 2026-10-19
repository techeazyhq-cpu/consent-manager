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

// Local stack only. Revokes every API key the kit issued ("Security test …"), at both test fiduciaries, including
// keys from earlier runs whose keys file is gone. Signs the owners in afresh; prints only ids.
//
//   node tests/security/teardown-keys.mjs

import { GW, dfSignIn } from './lib/cm1.mjs';

const owners = ['batch-owner@example.test', 'contact@security-b.example.test'];
for (const owner of owners) {
  const token = await dfSignIn(owner);
  const headers = { Authorization: `Bearer ${token}` };
  const keys = await (await fetch(`${GW}/api/df-reg/me/api-keys`, { headers })).json();
  const ours = keys.filter((k) => k.name?.startsWith('Security test') && !k.revokedAt);
  for (const k of ours) {
    const r = await fetch(`${GW}/api/df-reg/me/api-keys/${k.id}`, { method: 'DELETE', headers });
    console.log(`${owner}: ${k.id} ${r.ok ? 'revoked' : `not revoked (${r.status})`}`);
  }
  if (!ours.length) console.log(`${owner}: nothing to revoke`);
}
