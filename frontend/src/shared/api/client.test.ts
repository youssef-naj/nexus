import { afterEach, describe, expect, it, vi } from "vitest"
import { ApiError, apiFetch } from "./client"

afterEach(() => vi.unstubAllGlobals())

describe("apiFetch", () => {
  it("throws an ApiError carrying the problem details", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: "Not Found", status: 404 }), {
          status: 404,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    )
    await expect(apiFetch("/orgs/x")).rejects.toMatchObject({
      name: "ApiError",
      status: 404,
    })
    await expect(apiFetch("/orgs/x")).rejects.toBeInstanceOf(ApiError)
  })

  it("sends the CSRF header on unsafe methods", async () => {
    Object.defineProperty(document, "cookie", { value: "XSRF-TOKEN=abc123", configurable: true })
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal("fetch", fetchMock)

    await apiFetch("/auth/logout", { method: "POST" })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("abc123")
  })
})
