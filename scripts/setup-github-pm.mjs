#!/usr/bin/env node
// Phase 0 GitHub Project/Epic/Milestone/Backlog/Task/Sub-task setup.
// Idempotent and re-runnable: every step checks for existing state before creating anything.
// Reads scripts/github-pm-manifest.json. Requires `gh` CLI authenticated with repo+project scopes.

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
  sleep(350); // stay well under GitHub's secondary rate limits for content-creation endpoints
  return out;
}

function ghJson(args) {
  const out = gh(args);
  return out ? JSON.parse(out) : null;
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

// ---------- 1. Labels ----------
console.log("\n== Labels ==");
const existingLabels = new Set(ghJson(["label", "list", "--repo", nameWithOwner, "--json", "name", "--limit", "300"]).map((l) => l.name));
for (const l of manifest.labels) {
  if (existingLabels.has(l.name)) {
    console.log(`  [skip] label "${l.name}" exists`);
    continue;
  }
  const r = ghSafe(["label", "create", l.name, "--repo", nameWithOwner, "--color", l.color, "--description", l.description || ""]);
  console.log(r.ok ? `  [created] label "${l.name}"` : `  [warn] label "${l.name}": ${r.err}`);
}

// ---------- 2. Milestones ----------
console.log("\n== Milestones ==");
const existingMilestones = new Map();
{
  const r = ghSafe(["api", `repos/${owner}/${repo}/milestones`, "--paginate", "--jq", ".[] | [.title, .number] | @tsv"]);
  if (r.ok) {
    for (const line of r.out.split("\n").filter(Boolean)) {
      const [title, number] = line.split("\t");
      existingMilestones.set(title, Number(number));
    }
  }
}
for (const m of manifest.milestones) {
  if (existingMilestones.has(m.title)) {
    console.log(`  [skip] milestone "${m.title}" exists (#${existingMilestones.get(m.title)})`);
    continue;
  }
  const r = ghSafe(["api", `repos/${owner}/${repo}/milestones`, "-f", `title=${m.title}`, "-f", `description=${m.description || ""}`, "-f", "state=open", "--jq", ".number"]);
  if (r.ok) {
    existingMilestones.set(m.title, Number(r.out));
    console.log(`  [created] milestone "${m.title}" (#${r.out})`);
  } else {
    console.log(`  [warn] milestone "${m.title}": ${r.err}`);
  }
}

// ---------- 3. GitHub Project (v2) ----------
console.log("\n== Project ==");
let projectNumber = null;
{
  const r = ghSafe(["project", "list", "--owner", owner, "--format", "json"]);
  if (r.ok) {
    const projects = JSON.parse(r.out).projects || [];
    const existing = projects.find((p) => p.title === manifest.project.title);
    if (existing) {
      projectNumber = existing.number;
      console.log(`  [skip] project "${manifest.project.title}" exists (#${projectNumber})`);
    }
  }
  if (projectNumber === null) {
    const c = ghSafe(["project", "create", "--owner", owner, "--title", manifest.project.title, "--format", "json"]);
    if (c.ok) {
      projectNumber = JSON.parse(c.out).number;
      console.log(`  [created] project "${manifest.project.title}" (#${projectNumber})`);
    } else {
      console.log(`  [warn] could not create project: ${c.err}`);
    }
  }
}

function addToProject(issueUrl) {
  if (projectNumber === null) return;
  const r = ghSafe(["project", "item-add", String(projectNumber), "--owner", owner, "--url", issueUrl]);
  if (!r.ok) console.log(`    [warn] project item-add failed: ${r.err}`);
}

// ---------- Issue helpers ----------
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
  addToProject(created.url);
  return created;
}

function getIssueDbId(issueNumber) {
  const r = ghSafe(["api", `repos/${owner}/${repo}/issues/${issueNumber}`, "--jq", ".id"]);
  return r.ok ? Number(r.out) : null;
}

function appendTaskListLine(epicNumber, taskNumber) {
  const bodyR = ghSafe(["api", `repos/${owner}/${repo}/issues/${epicNumber}`, "--jq", ".body"]);
  const currentBody = bodyR.ok ? bodyR.out : "";
  const line = `- [ ] #${taskNumber}`;
  if (currentBody.includes(line)) return;
  const newBody = currentBody.endsWith("\n") || currentBody === "" ? `${currentBody}${line}\n` : `${currentBody}\n${line}\n`;
  const tmp = path.join(__dirname, `.tmp-body-${epicNumber}.md`);
  writeFileSync(tmp, newBody, "utf8");
  const r = ghSafe(["issue", "edit", String(epicNumber), "--repo", nameWithOwner, "--body-file", tmp]);
  unlinkSync(tmp);
  if (!r.ok) console.log(`    [warn] could not append task list line to epic #${epicNumber}: ${r.err}`);
}

function linkAsSubIssue(epicNumber, taskNumber) {
  const taskId = getIssueDbId(taskNumber);
  if (taskId === null) {
    appendTaskListLine(epicNumber, taskNumber);
    return;
  }
  const r = ghSafe(["api", `repos/${owner}/${repo}/issues/${epicNumber}/sub_issues`, "-X", "POST", "-F", `sub_issue_id=${taskId}`]);
  if (!r.ok) {
    console.log(`    [info] sub_issues API unavailable, falling back to task-list line for #${taskNumber}: ${r.err}`);
    appendTaskListLine(epicNumber, taskNumber);
  }
}

// ---------- 4/5/6. Phase-1 Epics + Tasks (sub-tasks are checklists inside each task body) ----------
console.log("\n== Phase 1 Epics + Tasks ==");
for (const epic of manifest.epics) {
  console.log(`\n-- Epic: ${epic.title} --`);
  const epicIssue = ensureIssue({ title: epic.title, body: epic.body, labels: epic.labels, milestone: epic.milestone });
  for (const task of epic.tasks) {
    const taskIssue = ensureIssue({
      title: task.title,
      body: task.body,
      labels: ["task", "phase-1", ...(epic.labels.includes("infra") ? ["infra"] : [])],
      milestone: epic.milestone,
    });
    linkAsSubIssue(epicIssue.number, taskIssue.number);
  }
}

// ---------- Phase-2 backlog epics (not decomposed into tasks) ----------
console.log("\n== Phase 2 Backlog Epics ==");
for (const be of manifest.backlogEpics) {
  ensureIssue({
    title: be.title,
    body: be.body,
    labels: ["epic", "backlog", "phase-2"],
    milestone: "Phase 2 — Platform Expansion",
  });
}

console.log("\nDone.");
