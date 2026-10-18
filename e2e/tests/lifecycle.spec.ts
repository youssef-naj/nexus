import { expect, test } from "../support/test"
import { ATLAS, futureDate, orgIdByName, unique } from "../support/session"

test("a request goes from draft to approved through four people, with every step in its history", async ({ cast }) => {
  const title = `E2E ergonomic mat ${unique()}`
  const sofia = await cast.open("sofia")
  const atlas = await orgIdByName(sofia, ATLAS)

  // Sofia (an employee) writes the request
  await sofia.goto(`/orgs/${atlas}/requests/new`)
  await sofia.getByLabel("Title").fill(title)
  await sofia.getByLabel("Description").fill("Created by the end-to-end test.\nSecond line.")
  await sofia.getByLabel("Category").selectOption("FACILITIES")
  await sofia.getByLabel("Department").selectOption({ label: "Facilities" })
  await sofia.getByLabel("Due date").fill(futureDate(10))
  await sofia.getByRole("button", { name: "Create request" }).click()
  await expect(sofia.getByRole("heading", { name: title })).toBeVisible()
  await expect(sofia.getByText("Draft", { exact: true })).toBeVisible()

  // ... submits it, and cannot review her own request
  await sofia.getByLabel("Comment (optional)").fill("Needed for the e2e test.")
  await sofia.getByRole("button", { name: "Submit for review" }).click()
  await expect(sofia.getByText("Request submitted for review.")).toBeVisible()
  await expect(sofia.getByText("Submitted", { exact: true })).toBeVisible()
  await expect(sofia.getByRole("button", { name: "Approve" })).toHaveCount(0)
  await expect(sofia.getByRole("link", { name: "Edit request" })).toHaveCount(0)

  // Priya (a manager) finds it in her review queue and sends it back; a comment is required
  const priya = await cast.open("priya")
  await priya.goto(`/orgs/${atlas}/requests?reviewable=true`)
  await priya.getByRole("link", { name: title }).click()
  await expect(priya.getByRole("heading", { name: "Review", exact: true })).toBeVisible()
  await priya.getByRole("button", { name: "Request changes" }).click()
  await expect(priya.getByText("Enter a comment explaining why.")).toBeVisible()
  await priya.getByLabel("Comment", { exact: true }).fill("Please add the model of the mat.")
  await priya.getByRole("button", { name: "Request changes" }).click()
  await expect(priya.getByText(/Changes requested\. The creator can now edit/)).toBeVisible()

  // Sofia sees it came back, edits it, and submits it again
  await sofia.reload()
  await expect(sofia.getByText("Changes requested", { exact: true })).toBeVisible()
  await sofia.getByRole("link", { name: "Edit request" }).click()
  await sofia.getByLabel("Description").fill("Created by the end-to-end test.\nModel: ErgoMat 3.")
  await sofia.getByRole("button", { name: "Save changes" }).click()
  await expect(sofia.getByText("Model: ErgoMat 3.")).toBeVisible()
  await sofia.getByRole("button", { name: "Submit for review" }).click()
  await expect(sofia.getByText("Request submitted for review.")).toBeVisible()

  // Priya assigns it to Lucas, who finds it in his queue
  await priya.reload()
  await priya.getByLabel("Assign to").selectOption({ label: "Lucas Meyer" })
  await priya.getByRole("button", { name: "Assign", exact: true }).click()
  await expect(priya.getByText("Assigned to Lucas Meyer.", { exact: true })).toBeVisible()
  const lucas = await cast.open("lucas")
  await lucas.goto(`/orgs/${atlas}/requests?assignedToMe=true`)
  await expect(lucas.getByRole("link", { name: title })).toBeVisible()

  // Daniel (an admin) approves it
  const daniel = await cast.open("daniel")
  await daniel.goto(`/orgs/${atlas}/requests?reviewable=true`)
  await daniel.getByRole("link", { name: title }).click()
  await daniel.getByRole("button", { name: "Approve" }).click()
  await expect(daniel.getByText("Request approved.")).toBeVisible()
  await expect(daniel.getByRole("button", { name: "Approve" })).toHaveCount(0)

  // Everything is in the history, in order, visible to its creator
  await sofia.reload()
  await expect(sofia.getByText("Approved", { exact: true })).toBeVisible()
  const events = sofia.getByRole("region", { name: "History" }).getByRole("listitem")
  await expect(events).toHaveCount(5)
  await expect(events).toContainText([
    "Sofia Rossi submitted the request",
    "Priya Raman requested changes",
    "Sofia Rossi submitted the request",
    "Priya Raman assigned the request to Lucas Meyer",
    "Daniel Okafor approved the request",
  ])
})
