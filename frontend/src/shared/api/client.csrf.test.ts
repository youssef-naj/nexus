import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { apiFetch } from "./client"

beforeEach(() => {
  // The shared setup sets a CSRF cookie; these tests start from a browser that has none.
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/"
})

afterEach(() => vi.unstubAllGlobals())

describe("apiFetch CSRF and rate-limit handling", () => {
  it("fetches a CSRF cookie first when the browser has none", async () => {
    // Typing the mock as `fetch` gives its calls the real [input, init] shape
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      if (String(input) === "/api/system/ping") {
        document.cookie = "XSRF-TOKEN=fresh-token; path=/"
        return new Response("{}", { status: 200 })
      }
      return new Response(null, { status: 204 })
    })
    vi.stubGlobal("fetch", fetchMock)

    await apiFetch("/auth/logout", { method: "POST" })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const init = fetchMock.mock.calls[1]?.[1]
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("fresh-token")
  })

  it("does not fetch a CSRF cookie for GET requests", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }))
    vi.stubGlobal("fetch", fetchMock)

    await apiFetch("/auth/me")

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("exposes Retry-After on a 429 response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 429 }), {
          status: 429,
          headers: { "Retry-After": "900" },
        }),
      ),
    )

    await expect(apiFetch("/auth/me")).rejects.toMatchObject({
      status: 429,
      retryAfterSeconds: 900,
    })
  })
})
