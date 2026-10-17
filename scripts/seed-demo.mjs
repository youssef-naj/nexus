#!/usr/bin/env node
/**
 * Builds a believable demo through the public API and the demo mail catcher (ADR-0035).
 * Needs the demo stack running (compose.demo.yaml). Node 20 or newer, no dependencies.
 *
 *   node scripts/seed-demo.mjs
 *
 * Environment: BASE (default http://localhost:8088), MAILPIT (default http://localhost:8026),
 * DEMO_PASSWORD (default below).
 */
const BASE = (process.env.BASE ?? "http://localhost:8088").replace(/\/$/, "")
const MAILPIT = (process.env.MAILPIT ?? "http://localhost:8026").replace(/\/$/, "")
const PASSWORD = process.env.DEMO_PASSWORD ?? "Demo-passphrase-2026"

const ATLAS = "Atlas Logistics"
const NORTHWIND = "Northwind Studio"

const PEOPLE = {
  amira: { name: "Amira Benali", email: "amira.benali@demo.example" },
  daniel: { name: "Daniel Okafor", email: "daniel.okafor@demo.example" },
  priya: { name: "Priya Raman", email: "priya.raman@demo.example" },
  lucas: { name: "Lucas Meyer", email: "lucas.meyer@demo.example" },
  sofia: { name: "Sofia Rossi", email: "sofia.rossi@demo.example" },
  tomas: { name: "Tomás Alvarez", email: "tomas.alvarez@demo.example" },
  hana: { name: "Hana Kobayashi", email: "hana.kobayashi@demo.example" },
  nora: { name: "Nora Lindqvist", email: "nora.lindqvist@demo.example" },
  platform: { name: "Platform Operator", email: "platform@demo.example" },
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const dueDate = (days) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10)

/** One browser-like session: keeps cookies, sends the CSRF header, waits out rate limits. */
class Api {
  #cookies = new Map()

  #remember(response) {
    for (const line of response.headers.getSetCookie()) {
      const [pair, ...attributes] = line.split(";")
      const index = pair.indexOf("=")
      const name = pair.slice(0, index).trim()
      const value = pair.slice(index + 1).trim()
      const expired = value === "" || attributes.some((a) => /^\s*max-age=0\s*$/i.test(a))
      if (expired) this.#cookies.delete(name)
      else this.#cookies.set(name, value)
    }
  }

  async #once(method, path, { json, form } = {}) {
    const headers = { Accept: "application/json" }
    let body
    if (json !== undefined) {
      headers["Content-Type"] = "application/json"
      body = JSON.stringify(json)
    } else if (form) {
      headers["Content-Type"] = "application/x-www-form-urlencoded"
      body = new URLSearchParams(form).toString()
    }
    if (method !== "GET" && this.#cookies.has("XSRF-TOKEN")) {
      headers["X-XSRF-TOKEN"] = this.#cookies.get("XSRF-TOKEN")
    }
    if (this.#cookies.size > 0) {
      headers.Cookie = [...this.#cookies].map(([name, value]) => `${name}=${value}`).join("; ")
    }
    const response = await fetch(BASE + path, { method, headers, body, redirect: "manual" })
    this.#remember(response)
    const text = await response.text()
    let parsed = null
    try {
      parsed = text ? JSON.parse(text) : null
    } catch {
      parsed = null
    }
    return {
      status: response.status,
      text,
      json: parsed,
      retryAfter: Number(response.headers.get("retry-after") ?? 0),
    }
  }

  async send(method, path, options) {
    if (method !== "GET" && !this.#cookies.has("XSRF-TOKEN")) {
      await this.#once("GET", "/api/system/ping")
    }
    let result
    for (let attempt = 0; attempt < 4; attempt++) {
      result = await this.#once(method, path, options)
      if (result.status !== 429) return result
      const wait = Math.min(Math.max(result.retryAfter, 1), 30)
      console.log(`      rate limited, waiting ${wait}s ...`)
      await sleep(wait * 1000)
    }
    return result
  }
}

function ok(result, allowed, what) {
  const accepted = Array.isArray(allowed) ? allowed : [allowed]
  if (!accepted.includes(result.status)) {
    throw new Error(`${what}: expected ${accepted.join(" or ")}, got ${result.status} ${result.text.slice(0, 300)}`)
  }
  return result.json
}

// ---- email: tokens come from the demo mail catcher, exactly as a person would read them -------

const usedMessages = new Set()

async function getJson(url) {
  const response = await fetch(url)
  return response.ok ? response.json() : null
}

async function mailToken(email, timeoutMs = 45_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const found = await getJson(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)
    for (const message of found?.messages ?? []) {
      if (usedMessages.has(message.ID)) continue
      const full = await getJson(`${MAILPIT}/api/v1/message/${message.ID}`)
      const text = `${full?.Text ?? ""}\n${full?.HTML ?? ""}`
      const match = /token=([A-Za-z0-9_.~-]+)/.exec(text)
      if (match) {
        usedMessages.add(message.ID)
        return match[1]
      }
    }
    await sleep(500)
  }
  throw new Error(`No email with a link arrived for ${email} in Mailpit (${MAILPIT})`)
}

async function createAccount(person) {
  const api = new Api()
  ok(await api.send("POST", "/api/auth/register", { json: { email: person.email, password: PASSWORD, displayName: person.name } }), 202, `register ${person.name}`)
  const token = await mailToken(person.email)
  ok(await api.send("POST", "/api/auth/verify-email", { json: { token } }), 204, `verify ${person.name}`)
  ok(await api.send("POST", "/api/auth/login", { form: { email: person.email, password: PASSWORD } }), 204, `sign in ${person.name}`)
  return api
}

// ---- organization building blocks ------------------------------------------------------------

async function createOrganization(owner, name) {
  return ok(await owner.send("POST", "/api/orgs", { json: { name } }), [200, 201], `create ${name}`).id
}

async function invite(owner, orgId, invitee, inviteeApi, role) {
  ok(await owner.send("POST", `/api/orgs/${orgId}/invitations`, { json: { email: invitee.email, role } }), [200, 201], `invite ${invitee.name}`)
  const token = await mailToken(invitee.email)
  ok(await inviteeApi.send("POST", "/api/invitations/accept", { json: { token } }), [200, 204], `accept as ${invitee.name}`)
}

async function memberIds(api, orgId) {
  const page = ok(await api.send("GET", `/api/orgs/${orgId}/members?size=100`), 200, "list members")
  return Object.fromEntries(page.content.map((member) => [member.displayName, member.id]))
}

async function createDepartment(api, orgId, name, description) {
  return ok(await api.send("POST", `/api/orgs/${orgId}/departments`, { json: { name, description } }), 201, `create department ${name}`).id
}

async function addToDepartment(api, orgId, departmentId, membershipId) {
  ok(await api.send("PUT", `/api/orgs/${orgId}/departments/${departmentId}/members/${membershipId}`), 204, "assign to department")
}

// ---- request building blocks -----------------------------------------------------------------

async function createRequest(api, orgId, r) {
  const created = ok(
    await api.send("POST", `/api/orgs/${orgId}/requests`, {
      json: {
        title: r.title,
        category: r.category,
        description: r.description,
        dueDate: r.days ? dueDate(r.days) : null,
        departmentId: r.department ?? null,
      },
    }),
    201,
    `create "${r.title}"`,
  )
  return { ...r, orgId, id: created.id, version: created.version }
}

async function act(api, request, action, comment) {
  const body = ok(
    await api.send("POST", `/api/orgs/${request.orgId}/requests/${request.id}/transitions`, {
      json: { action, version: request.version, comment },
    }),
    200,
    `${action} "${request.title}"`,
  )
  request.version = body.version
}

async function edit(api, request, changes) {
  Object.assign(request, changes)
  const body = ok(
    await api.send("PUT", `/api/orgs/${request.orgId}/requests/${request.id}`, {
      json: {
        title: request.title,
        category: request.category,
        description: request.description,
        dueDate: request.days ? dueDate(request.days) : null,
        departmentId: request.department ?? null,
        version: request.version,
      },
    }),
    200,
    `edit "${request.title}"`,
  )
  request.version = body.version
}

async function assign(api, request, membershipId) {
  const body = ok(
    await api.send("PUT", `/api/orgs/${request.orgId}/requests/${request.id}/assignee`, {
      json: { membershipId, version: request.version },
    }),
    200,
    `assign "${request.title}"`,
  )
  request.version = body.version
}

// ---- the story -------------------------------------------------------------------------------

function step(text) {
  console.log(`  - ${text}`)
}

async function main() {
  console.log(`Seeding the demo through ${BASE}`)

  const probe = new Api()
  const attempt = await probe.send("POST", "/api/auth/login", { form: { email: PEOPLE.amira.email, password: PASSWORD } })
  if (attempt.status === 204) {
    const existing = await probe.send("GET", "/api/orgs")
    if (Array.isArray(existing.json) && existing.json.some((org) => org.name === ATLAS)) {
      console.log("The demo data already exists (Amira can sign in and owns Atlas Logistics).")
      console.log("To start over: dc down -v, then up and seed again.")
      return
    }
  }

  console.log("Accounts (each one registers, reads its verification email, and signs in)")
  const accounts = {}
  for (const [key, person] of Object.entries(PEOPLE)) {
    step(person.name)
    accounts[key] = await createAccount(person)
  }

  console.log(`${ATLAS}`)
  const atlas = await createOrganization(accounts.amira, ATLAS)
  step("organization created")
  const roles = { daniel: "ADMIN", priya: "MANAGER", lucas: "MANAGER", sofia: "EMPLOYEE", tomas: "EMPLOYEE", hana: "EMPLOYEE" }
  for (const [key, role] of Object.entries(roles)) {
    await invite(accounts.amira, atlas, PEOPLE[key], accounts[key], role)
    step(`${PEOPLE[key].name} joined as ${role}`)
  }
  const ids = await memberIds(accounts.amira, atlas)
  const id = (key) => ids[PEOPLE[key].name]

  const dept = {
    operations: await createDepartment(accounts.amira, atlas, "Operations", "Warehouses, transport and day-to-day logistics."),
    finance: await createDepartment(accounts.amira, atlas, "Finance", "Budgets, reimbursements and supplier payments."),
    it: await createDepartment(accounts.amira, atlas, "IT & Infrastructure", "Devices, access and networks."),
    hr: await createDepartment(accounts.amira, atlas, "Human Resources", "Hiring, onboarding and people policies."),
    facilities: await createDepartment(accounts.amira, atlas, "Facilities", "Buildings, safety and equipment."),
    legacy: await createDepartment(accounts.amira, atlas, "Legacy Support", "Retired after the 2025 reorganization."),
  }
  ok(await accounts.amira.send("POST", `/api/orgs/${atlas}/departments/${dept.legacy}/deactivate`), 200, "deactivate department")
  step("six departments (one inactive)")
  const memberships = {
    operations: ["sofia", "hana", "lucas"],
    finance: ["tomas", "priya"],
    it: ["daniel"],
    hr: ["amira"],
    facilities: ["lucas"],
  }
  for (const [department, people] of Object.entries(memberships)) {
    for (const key of people) await addToDepartment(accounts.amira, atlas, dept[department], id(key))
  }
  step("people assigned to departments")

  const A = accounts
  const request = (api, r) => createRequest(api, atlas, r)

  const laptop = await request(A.sofia, { title: "Replace the cracked laptop screen", category: "IT_SUPPORT", days: 5, department: dept.it, description: "The screen of my work laptop cracked on Tuesday. I can work with an external monitor at my desk, but not on the warehouse floor." })
  await act(A.sofia, laptop, "SUBMIT")
  await act(A.priya, laptop, "APPROVE", "Approved. IT will contact you to arrange the swap.")

  const desk = await request(A.sofia, { title: "Request a standing desk", category: "FACILITIES", days: 14, department: dept.facilities, description: "My doctor recommends alternating between sitting and standing during the day." })
  await act(A.sofia, desk, "SUBMIT", "A doctor's note is available on request.")
  await act(A.priya, desk, "REQUEST_CHANGES", "Please say which workstation you sit at, so Facilities can check that the space allows it.")
  await edit(A.sofia, desk, { description: `${desk.description}\n\nI sit at workstation 14, next to the east window; there is about 1.6 m of free space behind it.` })
  await act(A.sofia, desk, "SUBMIT", "Added my workstation.")
  await assign(A.priya, desk, id("lucas"))

  const offsite = await request(A.tomas, { title: "Team offsite budget for Q1", category: "FINANCE", days: 30, department: dept.finance, description: "Two days for the finance team to plan the year: venue, travel and meals for eight people." })
  await act(A.tomas, offsite, "SUBMIT")
  await act(A.daniel, offsite, "REJECT", "The request exceeds the quarterly team-events allowance. Please resubmit with a reduced scope, or split it across two quarters.")

  const analytics = await request(A.tomas, { title: "Access to the shared analytics workspace", category: "IT_SUPPORT", days: 7, department: dept.it, description: "I need read access to the delivery-time dashboards to prepare the monthly cost report." })
  await act(A.tomas, analytics, "SUBMIT")
  await assign(A.priya, analytics, id("daniel"))

  await request(A.hana, { title: "Plan the parental leave handover", category: "HR", days: 40, department: dept.hr, description: "Draft: list of tasks and owners for the three months I will be away." })

  const badge = await request(A.hana, { title: "Visitor badge for the new contractor", category: "FACILITIES", days: 3, department: dept.facilities, description: "A contractor starts on Monday and needs access to the loading dock for two weeks." })
  await act(A.hana, badge, "SUBMIT")
  await act(A.lucas, badge, "APPROVE", "Badge printed. Collect it at reception.")

  const vpn = await request(A.sofia, { title: "VPN is slow from the Casablanca office", category: "IT_SUPPORT", days: 4, department: dept.it, description: "Since last week, loading the order screens takes more than a minute when connected through the VPN." })
  await act(A.sofia, vpn, "SUBMIT")

  const suppliers = await request(A.priya, { title: "Quarterly supplier review", category: "FINANCE", days: 20, department: dept.finance, description: "Review contracts and delivery performance of the five largest suppliers." })
  await act(A.priya, suppliers, "SUBMIT")
  await act(A.daniel, suppliers, "APPROVE")

  await request(A.lucas, { title: "Update the onboarding checklist", category: "HR", days: 25, department: dept.hr, description: "Draft: add the new safety briefing and the badge procedure." })

  const fire = await request(A.daniel, { title: "Renew the fire-safety certificate", category: "FACILITIES", days: 10, department: dept.facilities, description: "The certificate for the main warehouse expires next month." })
  await act(A.daniel, fire, "SUBMIT")
  await act(A.amira, fire, "APPROVE", "Please book the inspection for early next month.")

  const hiring = await request(A.amira, { title: "Hire two warehouse operators", category: "HR", days: 35, department: dept.operations, description: "Seasonal peak starts in six weeks; both positions are fixed-term for four months." })
  await act(A.amira, hiring, "SUBMIT")
  await assign(A.lucas, hiring, id("daniel"))

  const travel = await request(A.tomas, { title: "Reimburse conference travel", category: "FINANCE", days: 12, department: dept.finance, description: "Train and hotel for the logistics-software conference in Lyon." })
  await act(A.tomas, travel, "SUBMIT")
  await act(A.priya, travel, "REQUEST_CHANGES", "Please attach the receipts for the train tickets.")
  step("twelve requests in every state: drafts, awaiting review, assigned, sent back, approved, rejected")

  console.log(`${NORTHWIND}`)
  const northwind = await createOrganization(accounts.nora, NORTHWIND)
  await invite(accounts.nora, northwind, PEOPLE.amira, accounts.amira, "MANAGER")
  step("Amira is a member here too (use the organization switcher)")
  const design = await createDepartment(accounts.nora, northwind, "Design", "Brand, print and web.")
  await createDepartment(accounts.nora, northwind, "Client Services", "Projects and account management.")
  const brand = await createRequest(accounts.nora, northwind, { title: "Print budget for the brand refresh", category: "FINANCE", days: 15, department: design, description: "Business cards, stationery and signage for the new identity." })
  await act(accounts.nora, brand, "SUBMIT")
  await act(accounts.amira, brand, "APPROVE", "Approved within the Q4 marketing budget.")
  await createRequest(accounts.nora, northwind, { title: "Studio plant care contract", category: "FACILITIES", days: 45, department: null, description: "Draft: monthly watering and replacement of the lobby plants." })
  const licenses = await createRequest(accounts.amira, northwind, { title: "Renew the design software licenses", category: "IT_SUPPORT", days: 9, department: design, description: "Eight seats expire at the end of the month." })
  await act(accounts.amira, licenses, "SUBMIT")
  step("a few requests, one approved by the guest manager")

  const me = await accounts.platform.send("GET", "/api/auth/me")
  const platformReady = me.json?.platformAdmin === true

  console.log("\nDone. Everyone signs in with the demo password below (it is for this demo only).\n")
  console.log(`  Password: ${PASSWORD}`)
  console.log("  Role in Atlas Logistics:")
  console.log(`    Owner     ${PEOPLE.amira.email}  (also Manager in Northwind Studio)`)
  console.log(`    Admin     ${PEOPLE.daniel.email}`)
  console.log(`    Manager   ${PEOPLE.priya.email}, ${PEOPLE.lucas.email}`)
  console.log(`    Employee  ${PEOPLE.sofia.email}, ${PEOPLE.tomas.email}, ${PEOPLE.hana.email}`)
  console.log(`  Northwind Studio owner: ${PEOPLE.nora.email}`)
  console.log(`  Platform operator:      ${PEOPLE.platform.email}`)
  if (!platformReady) {
    console.log("\nThe platform operator role is granted when the backend starts. Restart it once:")
    console.log("    dc restart backend; dc up -d --wait")
  }
  console.log(`\nOpen ${BASE}  (mail at ${MAILPIT})`)
}

main().catch((error) => {
  console.error(`\nSeeding failed: ${error.message}`)
  console.error("\nIf this says 429, restart the backend (its rate limits are in memory) and run again from a clean stack.")
  console.error("A failed run leaves some accounts behind; reset the stack with 'dc down -v' before trying again.")
  process.exit(1)
})
