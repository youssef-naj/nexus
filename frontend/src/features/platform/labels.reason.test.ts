import { describe, expect, it } from "vitest"
import {
  EVENT_TYPES,
  PLATFORM_EVENT_TYPES,
  describeMetadata,
  eventLabel,
  reasonLabel,
} from "../audit/labels"

describe("reason labels", () => {
  it("names known reasons and still shows an unknown code", () => {
    expect(reasonLabel("NON_PAYMENT")).toBe("Non-payment")
    expect(reasonLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW")
  })

  it("describes a suspension with its reason", () => {
    expect(describeMetadata({ reason: "POLICY_VIOLATION" })).toBe("Reason: Policy violation")
  })
})

describe("event type lists", () => {
  it("keeps platform events out of an organization's filter and in the platform one", () => {
    expect(EVENT_TYPES).toContain("ORGANIZATION_SUSPENDED")
    expect(EVENT_TYPES.some((type) => type.startsWith("PLATFORM_"))).toBe(false)
    expect(PLATFORM_EVENT_TYPES).toContain("PLATFORM_ORGANIZATION_SUSPENDED")
    expect(PLATFORM_EVENT_TYPES.every((type) => type.startsWith("PLATFORM_"))).toBe(true)
  })

  it("labels the new events", () => {
    expect(eventLabel("PLATFORM_ADMIN_GRANTED")).toBe("Platform administrator added")
  })
})
