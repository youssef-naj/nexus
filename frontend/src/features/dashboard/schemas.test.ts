import { describe, expect, it } from "vitest"
import { countOf, dashboardSchema } from "./schemas"

describe("countOf", () => {
  it("returns zero for a status the server did not send", () => {
    expect(countOf({ DRAFT: 2 }, "DRAFT")).toBe(2)
    expect(countOf({ DRAFT: 2 }, "APPROVED")).toBe(0)
  })
})

describe("dashboardSchema", () => {
  const base = {
    scope: "MINE",
    total: 0,
    byStatus: {},
    mine: { total: 0, byStatus: {} },
    recent: [],
  }

  it("accepts a missing or null awaitingReview", () => {
    expect(dashboardSchema.parse(base).awaitingReview).toBeUndefined()
    expect(dashboardSchema.parse({ ...base, awaitingReview: null }).awaitingReview).toBeNull()
  })

  it("rejects an unknown scope", () => {
    expect(dashboardSchema.safeParse({ ...base, scope: "EVERYTHING" }).success).toBe(false)
  })
})
