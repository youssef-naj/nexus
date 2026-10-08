import { describe, expect, it } from "vitest"
import { hasControlCharacter, hasDisallowedControlCharacter } from "./text"

describe("control characters", () => {
  it("rejects every control character in single-line text", () => {
    expect(hasControlCharacter("plain text")).toBe(false)
    expect(hasControlCharacter("tab\there")).toBe(true)
    expect(hasControlCharacter("bell\u0007")).toBe(true)
    expect(hasControlCharacter("delete\u007f")).toBe(true)
  })

  it("allows newlines and tabs in multi-line text but nothing else", () => {
    expect(hasDisallowedControlCharacter("line one\nline two\ttabbed\r\n")).toBe(false)
    expect(hasDisallowedControlCharacter("bell\u0007")).toBe(true)
    expect(hasDisallowedControlCharacter("null\u0000")).toBe(true)
  })
})
