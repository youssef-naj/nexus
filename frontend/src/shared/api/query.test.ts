import { describe, expect, it } from "vitest"
import { buildQuery } from "./query"

describe("buildQuery", () => {
  it("returns nothing when there are no values", () => {
    expect(buildQuery({})).toBe("")
    expect(buildQuery({ a: undefined, b: null, c: "" })).toBe("")
  })

  it("keeps false and zero", () => {
    expect(buildQuery({ active: false, page: 0 })).toBe("?active=false&page=0")
  })

  it("encodes values so user text cannot inject parameters", () => {
    expect(buildQuery({ q: "a&b=c d%" })).toBe("?q=a%26b%3Dc+d%25")
  })
})
