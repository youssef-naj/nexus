import { describe, expect, it } from "vitest"
import { inviteSchema, memberSchema } from "./schemas"

describe("memberSchema", () => {
  it("accepts a member whose email is hidden from the viewer", () => {
    const parsed = memberSchema.parse({
      id: "1",
      userId: "u1",
      displayName: "Ada",
      email: null,
      role: "EMPLOYEE",
      joinedAt: "2026-10-01T10:00:00Z",
      version: 0,
      you: false,
    })

    expect(parsed.email).toBeNull()
  })
})

describe("inviteSchema", () => {
  it("trims the address", () => {
    expect(inviteSchema.parse({ email: "  new@example.com  ", role: "MANAGER" }).email).toBe(
      "new@example.com",
    )
  })

  it.each([
    ["a malformed address", "not-an-email"],
    ["an empty address", ""],
  ])("rejects %s", (_label, email) => {
    expect(inviteSchema.safeParse({ email, role: "EMPLOYEE" }).success).toBe(false)
  })

  it("rejects an unknown role", () => {
    expect(inviteSchema.safeParse({ email: "a@b.co", role: "KING" }).success).toBe(false)
  })
})
