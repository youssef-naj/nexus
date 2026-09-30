import { screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { renderWithQuery } from "@/test/utils"
import { BackendStatus } from "./BackendStatus"

afterEach(() => vi.unstubAllGlobals())

function stubFetch(response: Response) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response))
}

describe("BackendStatus", () => {
  it("shows the backend status when the ping succeeds", async () => {
    stubFetch(
      new Response(JSON.stringify({ status: "ok", serverTime: "2026-09-30T10:15:30Z" }), {
        status: 200,
      }),
    )
    renderWithQuery(<BackendStatus />)

    expect(await screen.findByText("Backend ok")).toBeInTheDocument()
  })

  it("shows an error state when the backend fails", async () => {
    stubFetch(new Response(JSON.stringify({ title: "Service Unavailable" }), { status: 503 }))
    renderWithQuery(<BackendStatus />)

    expect(await screen.findByText("Backend unreachable")).toBeInTheDocument()
  })
})
