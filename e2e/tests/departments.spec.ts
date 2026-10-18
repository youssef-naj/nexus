import { expect, test } from "../support/test"
import { ATLAS, orgIdByName, unique } from "../support/session"

test("an owner creates a department, adds a member, hits the duplicate rule, and deactivates it from the keyboard", async ({ cast }) => {
  const amira = await cast.open("amira")
  const atlas = await orgIdByName(amira, ATLAS)
  const name = `E2E Department ${unique()}`

  await amira.goto(`/orgs/${atlas}/departments`)
  await amira.getByLabel("Name", { exact: true }).fill(name)
  await amira.getByLabel("Description").fill("Created by the end-to-end test")
  await amira.getByRole("button", { name: "Create department" }).click()
  await expect(amira.getByText("Department created.")).toBeVisible()

  // The same name in other letters is refused, with the message under the field
  await amira.getByLabel("Name", { exact: true }).fill(name.toUpperCase())
  await amira.getByRole("button", { name: "Create department" }).click()
  await expect(amira.getByText("A department with this name already exists.")).toBeVisible()

  // Find it (the list is paginated and sorted by name) and open it
  await amira.getByLabel("Search departments").fill(name)
  await amira.getByRole("button", { name: "Search" }).click()
  await amira.getByRole("link", { name }).click()
  await expect(amira.getByRole("heading", { name })).toBeVisible()

  await amira.getByLabel("Add a member").selectOption({ label: "Hana Kobayashi" })
  await amira.getByRole("button", { name: "Add member" }).click()
  await expect(amira.getByText("Added Hana Kobayashi.")).toBeVisible()
  await expect(amira.getByRole("table")).toContainText("Hana Kobayashi")

  // Deactivating asks for confirmation, and works with the keyboard alone
  await amira.getByRole("button", { name: "Deactivate department" }).press("Enter")
  await amira.getByRole("button", { name: "Confirm deactivation" }).press("Enter")
  await expect(amira.getByText("Inactive", { exact: true })).toBeVisible()
  await expect(amira.getByText("Inactive departments cannot receive new members.")).toBeVisible()
  await expect(amira.getByLabel("Add a member")).toHaveCount(0)
  await expect(amira.getByRole("button", { name: "Reactivate department" })).toBeVisible()

  // Inactive departments leave the default list but stay findable
  await amira.goto(`/orgs/${atlas}/departments`)
  await amira.getByLabel("Search departments").fill(name)
  await amira.getByRole("button", { name: "Search" }).click()
  await expect(amira.getByText("No departments found.")).toBeVisible()
  await amira.getByLabel("Show").selectOption("inactive")
  await expect(amira.getByRole("link", { name })).toBeVisible()
})
