import { describe, expect, it } from "vitest"
import { createOrganizationSchema, organizationDetailSchema } from "./schemas"

describe("createOrganizationSchema", () => {
  it("trims the name", () => {
    expect(createOrganizationSchema.parse({ name: "  Acme Corp  " }).name).toBe("Acme Corp")
  })

  it.each([
    ["an empty name", ""],
    ["a blank name", "    "],
    ["a name over 120 characters", "a".repeat(121)],
    ["a name with a control character", "bad\u0007name"],
  ])("rejects %s", (_label, name) => {
    expect(createOrganizationSchema.safeParse({ name }).success).toBe(false)
  })
})

describe("organizationDetailSchema", () => {
  it("accepts permissions this client does not know yet", () => {
    const parsed = organizationDetailSchema.parse({
      id: "1",
      name: "Acme",
      slug: "acme",
      role: "OWNER",
      status: "ACTIVE",
      permissions: ["ORGANIZATION_VIEW", "SOMETHING_NEW"],
    })

    expect(parsed.permissions).toContain("SOMETHING_NEW")
  })

  it("rejects an unknown role", () => {
    expect(
      organizationDetailSchema.safeParse({
        id: "1",
        name: "Acme",
        slug: "acme",
        role: "KING",
        status: "ACTIVE",
        permissions: [],
      }).success,
    ).toBe(false)
  })
})
