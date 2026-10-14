import { describe, expect, it } from "vitest"
import { platformOrganizationSchema, REACTIVATE_REASONS, SUSPEND_REASONS } from "./schemas"

describe("platform schemas", () => {
  it("accepts the metadata the server returns", () => {
    const parsed = platformOrganizationSchema.parse({
      id: "o-1",
      name: "Acme",
      slug: "acme",
      status: "SUSPENDED",
      memberCount: 4,
      createdAt: "2026-10-01T10:00:00Z",
      version: 2,
    })

    expect(parsed.memberCount).toBe(4)
  })

  it("rejects an unknown status", () => {
    expect(
      platformOrganizationSchema.safeParse({
        id: "o-1",
        name: "Acme",
        slug: "acme",
        status: "DELETED",
        memberCount: 1,
        createdAt: "x",
        version: 0,
      }).success,
    ).toBe(false)
  })

  it("offers different reasons for suspending and reactivating", () => {
    expect(SUSPEND_REASONS).toContain("POLICY_VIOLATION")
    expect(SUSPEND_REASONS).not.toContain("ISSUE_RESOLVED")
    expect(REACTIVATE_REASONS).toContain("ISSUE_RESOLVED")
    expect(REACTIVATE_REASONS).not.toContain("NON_PAYMENT")
  })
})
