import { describe, expect, it } from "vitest"
import { canManageMember, grantableRoles } from "./roles"

describe("grantableRoles", () => {
  it("lets an owner grant every role", () => {
    expect(grantableRoles("OWNER")).toEqual(["OWNER", "ADMIN", "MANAGER", "EMPLOYEE"])
  })

  it("lets an admin grant only roles below admin", () => {
    expect(grantableRoles("ADMIN")).toEqual(["MANAGER", "EMPLOYEE"])
  })

  it("gives managers and employees nothing to grant", () => {
    expect(grantableRoles("MANAGER")).toEqual([])
    expect(grantableRoles("EMPLOYEE")).toEqual([])
  })
})

describe("canManageMember", () => {
  it("never lets an admin touch an admin or an owner", () => {
    expect(canManageMember("ADMIN", "ADMIN")).toBe(false)
    expect(canManageMember("ADMIN", "OWNER")).toBe(false)
    expect(canManageMember("ADMIN", "EMPLOYEE")).toBe(true)
  })

  it("lets an owner touch everyone", () => {
    expect(canManageMember("OWNER", "OWNER")).toBe(true)
  })
})
