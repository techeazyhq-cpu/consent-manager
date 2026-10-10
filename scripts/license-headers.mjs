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

/**
 * Checks that every tracked source file starts with the Apache 2.0 license header (CONTRIBUTING.md), in the comment
 * syntax of its type, after a shebang or doctype if it has one. CI runs it; `--fix` adds the missing headers.
 *
 * Not covered: files that cannot carry comments (JSON), third-party files (the Gradle wrapper, the pnpm lockfile),
 * documentation, and Flyway migrations: editing an applied migration changes its checksum and Flyway then refuses to
 * start against databases that already ran it.
 *
 *   node scripts/license-headers.mjs          # list files without the header, exit 1 if any
 *   node scripts/license-headers.mjs --fix    # add the header to them
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

const LINES = [
  'Copyright 2026 Vasantha Kumar',
  '',
  'Licensed under the Apache License, Version 2.0 (the "License");',
  'you may not use this file except in compliance with the License.',
  'You may obtain a copy of the License at',
  '',
  '    http://www.apache.org/licenses/LICENSE-2.0',
  '',
  'Unless required by applicable law or agreed to in writing, software',
  'distributed under the License is distributed on an "AS IS" BASIS,',
  'WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.',
  'See the License for the specific language governing permissions and',
  'limitations under the License.',
  '',
  '@author Vasantha Kumar <vasantha.kumar@hotmail.com>',
];
const MARKER = 'Licensed under the Apache License, Version 2.0';

const block = () => ['/*', ...LINES.map((l) => (l ? ` * ${l}` : ' *')), ' */', ''].join('\n');
const prefixed = (p) => [...LINES.map((l) => (l ? `${p} ${l}` : p)), ''].join('\n');
const wrapped = (open, close) => [open, ...LINES.map((l) => (l ? `  ${l}` : '')), close, ''].join('\n');

const STYLES = [
  [/\.(java|kts|ts|tsx|js|mjs|css)$/, block],
  [/(\.(ya?ml|sh|rego|properties|toml|imports)|\/Dockerfile[^/]*|META-INF\/services\/[^/]+)$/, () => prefixed('#')],
  [/\.sql$/, () => prefixed('--')],
  [/\.ftl$/, () => wrapped('<#--', '-->')],
  [/\.html$/, () => wrapped('<!--', '-->')],
];

const EXCLUDED = [
  /^gradlew(\.bat)?$/,
  /^gradle\/wrapper\//,
  /pnpm-lock\.yaml$/,
  /\/db\/migration\/[^/]+\.sql$/,
  /^\.claude\//,
  /^\.idea\//,
];

const headerFor = (path) => STYLES.find(([pattern]) => pattern.test(path))?.[1]();

/** Keeps a shebang or doctype as the first line, as the file's interpreter or browser requires. */
function withHeader(content, header) {
  const lead = content.match(/^(#![^\n]*\n|<!doctype[^>]*>\r?\n)/i)?.[0] ?? '';
  const rest = content.slice(lead.length);
  return `${lead}${header}${lead || !/^\r?\n/.test(rest) ? '\n' : ''}${rest}`.replace(`${header}\n\n\n`, `${header}\n\n`);
}

const fix = process.argv.includes('--fix');
const files = execFileSync('git', ['ls-files'], { encoding: 'utf8' }).split('\n').filter(Boolean);
const missing = [];
for (const path of files) {
  if (EXCLUDED.some((pattern) => pattern.test(path))) continue;
  const header = headerFor(path);
  if (!header) continue;
  const content = readFileSync(path, 'utf8');
  if (content.split('\n').slice(0, 25).some((line) => line.includes(MARKER))) continue;
  missing.push(path);
  if (fix) writeFileSync(path, withHeader(content, header));
}

if (missing.length === 0) {
  console.log('Every source file carries the license header.');
} else if (fix) {
  console.log(`Added the license header to ${missing.length} file(s).`);
} else {
  console.log(`${missing.length} file(s) without the license header (run with --fix to add it):`);
  missing.forEach((path) => console.log(`  ${path}`));
  process.exit(1);
}
