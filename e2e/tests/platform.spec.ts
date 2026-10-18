import { expect, test } from "../support/test"
import { NORTHWIND, orgIdByName } from "../support/session"
import { reactivateSuspendedOrganizations } from "../support/platform"

// Whatever happened in the test, never leave a demo organization suspended
test.afterEach(async () => {
  await reactivateSuspendedOrganizations()
})

test("the platform operator suspends an organization, its members are blocked at once, and reactivating restores them", async ({ cast }) => {
  const platform = await cast.open("platform")
  const me = (await (await platform.context().request.get("/api/auth/me")).json()) as { platformAdmin: boolean }
  test.skip(!me.platformAdmin, "Restart the backend once after seeding so the platform operator role is granted")
  const nora = await cast.open("nora")
  const northwind = await orgIdByName(nora, NORTHWIND)

  await platform.goto("/platform")
  await expect(platform.getByRole("heading", { name: "Organizations" })).toBeVisible()

  // Suspend: a reason is required before the confirmation can be used
  await platform.getByRole("button", { name: `Suspend ${NORTHWIND}` }).click()
  const confirm = platform.getByRole("button", { name: `Confirm suspension of ${NORTHWIND}` })
  await expect(confirm).toBeDisabled()
  await platform.getByLabel(`Reason for suspending ${NORTHWIND}`).selectOption("NON_PAYMENT")
  await confirm.click()
  await expect(platform.getByText(`${NORTHWIND} suspended.`)).toBeVisible()

  // The organization's own owner is blocked on the next request
  await nora.goto(`/orgs/${northwind}`)
  await expect(nora.getByText(/suspended/i).first()).toBeVisible()
  expect((await nora.context().request.get(`/api/orgs/${northwind}/requests`)).status()).toBe(403)

  // Reactivate through the UI, and the owner is back at work
  await platform.getByRole("button", { name: `Reactivate ${NORTHWIND}` }).click()
  await platform.getByLabel(`Reason for reactivating ${NORTHWIND}`).selectOption("ISSUE_RESOLVED")
  await platform.getByRole("button", { name: `Confirm reactivation of ${NORTHWIND}` }).click()
  await expect(platform.getByText(`${NORTHWIND} reactivated.`)).toBeVisible()

  await nora.goto(`/orgs/${northwind}`)
  await expect(nora.getByRole("heading", { name: NORTHWIND })).toBeVisible()
  expect((await nora.context().request.get(`/api/orgs/${northwind}/requests`)).status()).toBe(200)
})
