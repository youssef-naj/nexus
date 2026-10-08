import { describe, expect, it } from "vitest"
import { requestDetailSchema, requestFormSchema } from "./schemas"

const valid = {
  title: "Replace my laptop",
  description: "",
  category: "IT_SUPPORT",
  dueDate: "",
  departmentId: "",
}

describe("requestFormSchema", () => {
  it("accepts a minimal request and an optional date", () => {
    expect(requestFormSchema.safeParse(valid).success).toBe(true)
    expect(requestFormSchema.safeParse({ ...valid, dueDate: "2026-12-31" }).success).toBe(true)
  })

  it.each([
    ["a blank title", { title: "   " }],
    ["a title over 150 characters", { title: "x".repeat(151) }],
    ["a title with a control character", { title: "bad\u0007" }],
    ["a description over 5000 characters", { description: "d".repeat(5001) }],
    ["no category", { category: "" }],
    ["an unknown category", { category: "GARDENING" }],
    ["a malformed date", { dueDate: "12/31/2026" }],
  ])("rejects %s", (_label, change) => {
    expect(requestFormSchema.safeParse({ ...valid, ...change }).success).toBe(false)
  })

  it("allows line breaks in the description", () => {
    expect(requestFormSchema.safeParse({ ...valid, description: "one\ntwo" }).success).toBe(true)
  })
})

describe("requestDetailSchema", () => {
  it("accepts a request without department, assignee or due date", () => {
    const parsed = requestDetailSchema.parse({
      id: "1",
      reference: "REQ-000001",
      title: "T",
      category: "HR",
      status: "DRAFT",
      createdByMembershipId: "m1",
      createdByName: "Ada",
      createdAt: "2026-10-01T10:00:00Z",
      updatedAt: "2026-10-01T10:00:00Z",
      version: 0,
      editable: true,
    })

    expect(parsed.departmentName).toBeUndefined()
  })
})
