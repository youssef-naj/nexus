import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { apiFetch } from "./client"
import { commonErrorMessage } from "./messages"

afterEach(() => vi.unstubAllGlobals())

async function failureOf(status: number): Promise<unknown> {
  mockApi({ "GET /anything": () => jsonResponse(status, { status }) })
  try {
    await apiFetch("/anything")
    return null
  } catch (error) {
    return error
  }
}

describe("commonErrorMessage during outages", () => {
  it("tells people the service is temporarily unavailable on a 503", async () => {
    expect(commonErrorMessage(await failureOf(503))).toBe(
      "The service is temporarily unavailable. Please try again in a moment.",
    )
  })

  it("keeps the generic message for other server errors", async () => {
    expect(commonErrorMessage(await failureOf(500))).toBe(
      "Something went wrong on our side. Please try again.",
    )
  })
})
