import { describe, expect, it } from "vitest"
import { knownActions } from "./labels"
import { requestDetailSchema, requestEventSchema } from "./schemas"

const base = {
  id: "1",
  reference: "REQ-000001",
  title: "T",
  category: "HR",
  status: "SUBMITTED",
  createdByMembershipId: "m1",
  createdByName: "Ada",
  createdAt: "2026-10-01T10:00:00Z",
  updatedAt: "2026-10-01T10:00:00Z",
  version: 1,
  editable: false,
}

describe("request workflow schemas", () => {
  it("defaults to no actions when the server sends none", () => {
    expect(requestDetailSchema.parse(base).actions).toEqual([])
  })

  it("keeps only the actions this client knows", () => {
    const parsed = requestDetailSchema.parse({
      ...base,
      actions: ["APPROVE", "TELEPORT", "REJECT"],
    })

    expect(knownActions(parsed)).toEqual(["APPROVE", "REJECT"])
  })

  it("accepts an event with an action it has not heard of", () => {
    const parsed = requestEventSchema.parse({
      id: "e1",
      action: "ESCALATE",
      fromStatus: "SUBMITTED",
      toStatus: "SUBMITTED",
      actorMembershipId: "m1",
      actorName: "Ada",
      occurredAt: "2026-10-01T10:00:00Z",
    })

    expect(parsed.comment).toBeUndefined()
  })
})
