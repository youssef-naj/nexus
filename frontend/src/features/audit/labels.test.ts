import { describe, expect, it } from "vitest"
import { describeMetadata, eventLabel } from "./labels"

describe("eventLabel", () => {
  it("names known events", () => {
    expect(eventLabel("REQUEST_APPROVED")).toBe("Request approved")
  })

  it("still displays an event type it does not know", () => {
    expect(eventLabel("SOMETHING_NEW_HAPPENED")).toBe("Something new happened")
  })
})

describe("describeMetadata", () => {
  it("summarizes a role change with readable role names", () => {
    expect(describeMetadata({ fromRole: "EMPLOYEE", toRole: "MANAGER" })).toBe(
      "Role: Employee → Manager",
    )
  })

  it("summarizes an organization or a rename", () => {
    expect(describeMetadata({ name: "Acme", slug: "acme" })).toBe("Name: Acme · Slug: acme")
    expect(describeMetadata({ fromName: "Ops", toName: "Operations" })).toBe(
      "Renamed: Ops → Operations",
    )
  })

  it("shows a request reference and a single role", () => {
    expect(describeMetadata({ reference: "REQ-000005" })).toBe("Request: REQ-000005")
    expect(describeMetadata({ role: "ADMIN" })).toBe("Role: Admin")
  })

  it("never shows identifiers and is empty when there is nothing to say", () => {
    expect(describeMetadata({ membershipId: "6f1c0e4a-aaaa-bbbb-cccc-1234567890ab" })).toBe("")
    expect(describeMetadata({})).toBe("")
  })
})
