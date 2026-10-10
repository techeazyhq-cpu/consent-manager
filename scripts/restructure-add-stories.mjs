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

// Restructures the Phase 0 GitHub setup to Epic > Story > Task > Sub-task (checklist).
// Idempotent: safe to re-run. Reads the "stories" arrays added to github-pm-manifest.json.

import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, unlinkSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(readFileSync(path.join(__dirname, "github-pm-manifest.json"), "utf8"));

function sleep(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function gh(args, opts = {}) {
  const out = execFileSync("gh", args, { encoding: "utf8", ...opts }).trim();
  sleep(350);
  return out;
}

function ghSafe(args, opts = {}) {
  try {
    return { ok: true, out: gh(args, opts) };
  } catch (e) {
    return { ok: false, out: "", err: e.stderr || e.message };
  }
}

const nameWithOwner = gh(["repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"]);
const [owner, repo] = nameWithOwner.split("/");
console.log(`Target repo: ${owner}/${repo}`);

function ensureLabel(l) {
  const existing = ghSafe(["label", "list", "--repo", nameWithOwner, "--json", "name", "-q", `.[] | select(.name=="${l.name}") | .name`]);
  if (existing.ok && existing.out) {
    console.log(`  [skip] label "${l.name}" exists`);
    return;
  }
  const r = ghSafe(["label", "create", l.name, "--repo", nameWithOwner, "--color", l.color, "--description", l.description || ""]);
  console.log(r.ok ? `  [created] label "${l.name}"` : `  [warn] label "${l.name}": ${r.err}`);
}

function findIssueByExactTitle(title) {
  const r = ghSafe(["issue", "list", "--repo", nameWithOwner, "--state", "all", "--search", `"${title}" in:title`, "--json", "number,title,url", "--limit", "20"]);
  if (!r.ok) return null;
  const list = JSON.parse(r.out);
  return list.find((i) => i.title === title) || null;
}

function createIssue({ title, body, labels, milestone }) {
  const args = ["issue", "create", "--repo", nameWithOwner, "--title", title, "--body", body || title];
  if (labels && labels.length) args.push("--label", labels.join(","));
  if (milestone) args.push("--milestone", milestone);
  const url = gh(args);
  const number = Number(url.trim().split("/").pop());
  return { number, url: url.trim() };
}

function ensureIssue({ title, body, labels, milestone }) {
  const existing = findIssueByExactTitle(title);
  if (existing) {
    console.log(`  [skip] issue "${title}" exists (#${existing.number})`);
    return existing;
  }
  const created = createIssue({ title, body, labels, milestone });
  console.log(`  [created] issue "${title}" (#${created.number})`);
  return created;
}

function getIssueDbId(issueNumber) {
  const r = ghSafe(["api", `repos/${owner}/${repo}/issues/${issueNumber}`, "--jq", ".id"]);
  return r.ok ? Number(r.out) : null;
}

function linkAsSubIssue(parentNumber, childNumber) {
  const childId = getIssueDbId(childNumber);
  if (childId === null) return false;
  const r = ghSafe(["api", `repos/${owner}/${repo}/issues/${parentNumber}/sub_issues`, "-X", "POST", "-F", `sub_issue_id=${childId}`]);
  return r.ok;
}

function removeAsSubIssue(parentNumber, childNumber) {
  // Endpoint is singular "sub_issue" (not "sub_issues/{id}"), and sub_issue_id is a body
  // field, not a path segment — the plural/path-param form silently 404s.
  const childId = getIssueDbId(childNumber);
  if (childId === null) return false;
  const r = ghSafe(["api", `repos/${owner}/${repo}/issues/${parentNumber}/sub_issue`, "-X", "DELETE", "-F", `sub_issue_id=${childId}`]);
  return r.ok;
}

function appendChecklistLine(parentNumber, childNumber) {
  const bodyR = ghSafe(["api", `repos/${owner}/${repo}/issues/${parentNumber}`, "--jq", ".body"]);
  const currentBody = bodyR.ok ? bodyR.out : "";
  const line = `- [ ] #${childNumber}`;
  if (currentBody.includes(line)) return;
  const newBody = currentBody.endsWith("\n") || currentBody === "" ? `${currentBody}${line}\n` : `${currentBody}\n${line}\n`;
  const tmp = path.join(__dirname, `.tmp-body-${parentNumber}.md`);
  writeFileSync(tmp, newBody, "utf8");
  ghSafe(["issue", "edit", String(parentNumber), "--repo", nameWithOwner, "--body-file", tmp]);
  unlinkSync(tmp);
}

console.log("\n== Ensuring 'story' label ==");
const storyLabel = manifest.labels.find((l) => l.name === "story");
if (storyLabel) ensureLabel(storyLabel);

console.log("\n== Restructuring Epic > Story > Task ==");
for (const epic of manifest.epics) {
  if (!epic.stories || epic.stories.length === 0) continue;
  console.log(`\n-- Epic: ${epic.title} --`);
  const epicIssue = findIssueByExactTitle(epic.title);
  if (!epicIssue) {
    console.log(`  [warn] epic issue not found, skipping`);
    continue;
  }
  const ctxLabel = epic.labels.find((l) => !["epic", "phase-1", "phase-2"].includes(l));

  for (const story of epic.stories) {
    const storyIssue = ensureIssue({
      title: story.title,
      body: story.body,
      labels: ["story", "phase-1", ...(ctxLabel ? [ctxLabel] : [])],
      milestone: epic.milestone,
    });

    if (!linkAsSubIssue(epicIssue.number, storyIssue.number)) {
      appendChecklistLine(epicIssue.number, storyIssue.number);
    }

    for (const taskTitle of story.taskTitles) {
      const taskDef = epic.tasks.find((t) => t.title === taskTitle);
      const taskIssue = ensureIssue({
        title: taskTitle,
        body: taskDef ? taskDef.body : taskTitle,
        labels: ["task", "phase-1", ...(ctxLabel ? [ctxLabel] : [])],
        milestone: epic.milestone,
      });

      // Best-effort re-parent: remove from the epic (if it landed there directly in Phase 0),
      // then attach under the story. If either call fails, fall back to a checklist line so
      // the relationship is still visible somewhere.
      removeAsSubIssue(epicIssue.number, taskIssue.number);
      if (!linkAsSubIssue(storyIssue.number, taskIssue.number)) {
        appendChecklistLine(storyIssue.number, taskIssue.number);
      }
    }
  }
}

console.log("\nDone.");
