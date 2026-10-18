# The demo

A seeded, production-shaped stack on plain HTTP, for looking around and for the end-to-end tests. It is **not** a deployment (shared password, no real mail).

## Run it

```powershell
function dc { docker compose --env-file .env.demo -f compose.prod.yaml -f compose.demo.yaml @args }
Copy-Item .env.demo.example .env.demo
dc up -d --build --wait
node scripts/seed-demo.mjs
dc restart backend
```

The restart applies the platform operator role. Open http://localhost:8088. Email appears at http://localhost:8026. Everyone's password is `Demo-passphrase-2026`.

Reset to a clean demo: `dc down -v`, then start and seed again. The seed stops if the data already exists.

## The story

**Atlas Logistics** (owner: Amira). Six departments, one of them inactive (Legacy Support). Twelve requests:

| State | Requests |
|---|---|
| Draft | Plan the parental leave handover (Hana); Update the onboarding checklist (Lucas) |
| Awaiting review | Request a standing desk (Sofia, assigned to Lucas, after a round trip); Access to the shared analytics workspace (Tomás, assigned to Daniel); VPN is slow from the Casablanca office (Sofia); Hire two warehouse operators (Amira, assigned to Daniel) |
| Changes requested | Reimburse conference travel (Tomás) |
| Approved | Replace the cracked laptop screen; Visitor badge for the new contractor; Quarterly supplier review; Renew the fire-safety certificate |
| Rejected | Team offsite budget for Q1 |

**Northwind Studio** (owner: Nora). Amira is a Manager here too. Three requests, one awaiting Nora's review (submitted by Amira).

## A guided tour

1. **As Sofia (Employee):** the dashboard says "Your requests". Open Requests: only hers are listed. Open "Request a standing desk" to see its history (submitted, changes requested, submitted again, assigned). Try pasting the address of someone else's request: "not found".
2. **As Priya (Manager):** the dashboard shows "Awaiting your review". Click it, open the VPN request, and try Reject without a comment (a comment is required). Request changes with a comment. Notice the Assignment panel.
3. **Back as Sofia:** the request is now "Changes requested" and editable. Edit it and submit again.
4. **As Daniel (Admin):** approve something. Open Members and the Audit tab to see what was recorded.
5. **As Amira (Owner):** open "Hire two warehouse operators" (her own request): no approve or reject buttons, because nobody reviews their own request. Switch to Northwind with the organization switcher.
6. **As Nora (Owner of Northwind):** approve Amira's license request there.
7. **As the platform operator:** open Platform. You see organizations and their member counts, nothing else. Suspend Northwind (choose a reason), then, in another browser as Nora, watch it stop working. Reactivate it.
8. **Narrow the window** to phone width, and try the keyboard: the first Tab stop is a skip link.

The end-to-end tests add records named "E2E ..." to this data each time they run.
