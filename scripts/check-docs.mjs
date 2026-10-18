#!/usr/bin/env node
// Documentation checks: every relative link resolves, every decision record is in the index,
// decision numbers have no gaps, and each record has a status and a decision.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs"
import { dirname, join, relative, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..")
const problems = []
const fail = (message) => problems.push(message)

function markdownFiles(dir) {
  const found = []
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) found.push(...markdownFiles(path))
    else if (name.endsWith(".md")) found.push(path)
  }
  return found
}

const files = [join(root, "README.md"), ...markdownFiles(join(root, "docs"))]

// ---- links -------------------------------------------------------------------------------
for (const file of files) {
  const text = readFileSync(file, "utf8")
    .replace(/````[\s\S]*?````/g, "")
    .replace(/```[\s\S]*?```/g, "")
    .replace(/`[^`\n]*`/g, "")
  for (const match of text.matchAll(/!?\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
    const target = match[1]
    if (/^(https?:|mailto:|#)/.test(target)) continue
    const path = target.split("#")[0].split("?")[0]
    if (!path) continue
    const resolved = path.startsWith("/") ? join(root, path) : resolve(dirname(file), path)
    if (!existsSync(resolved)) fail(`${relative(root, file)}: broken link to ${target}`)
  }
}

// ---- decision records --------------------------------------------------------------------
// 0000 is the template; the records themselves are 0001, 0002, ... without gaps.
const decisions = join(root, "docs", "decisions")
if (!existsSync(join(decisions, "0000-template.md"))) fail("decisions: 0000-template.md is missing")

const records = readdirSync(decisions)
  .map((name) => /^(\d{4})-.+\.md$/.exec(name))
  .filter(Boolean)
  .map((match) => ({ number: Number(match[1]), name: match[0] }))
  .filter((record) => record.number > 0)
  .sort((a, b) => a.number - b.number)

records.forEach((record, index) => {
  if (record.number !== index + 1) {
    fail(`decision records: expected number ${index + 1}, found ${record.name}`)
  }
})

const index = readFileSync(join(decisions, "README.md"), "utf8")
const indexed = new Set([...index.matchAll(/\]\((\d{4}-[^)]+\.md)\)/g)].map((match) => match[1]))
for (const record of records) {
  if (!indexed.has(record.name)) fail(`decisions/README.md does not list ${record.name}`)
  const text = readFileSync(join(decisions, record.name), "utf8")
  if (!/status/i.test(text)) fail(`${record.name}: no status`)
  if (!/^##\s+Decision/m.test(text)) fail(`${record.name}: no "## Decision" section`)
}

if (problems.length > 0) {
  console.error(`Documentation problems (${problems.length}):`)
  for (const problem of problems) console.error(`  - ${problem}`)
  process.exit(1)
}
console.log(`Documentation OK: ${files.length} files, ${records.length} decision records`)
