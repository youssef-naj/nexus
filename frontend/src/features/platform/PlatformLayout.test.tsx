import { screen } from "@testing-library/react"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { PlatformLayout } from "./PlatformLayout"

afterEach(() => vi.unstubAllGlobals())

function render(platformAdmin: boolean) {
  const fetchMock = mockApi({
    "GET /auth/me": () =>
      jsonResponse(200, { id: "1", email: "a@b.co", displayName: "Ada", platformAdmin }),
  })
  renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>Login</p>} />
      <Route element={<RequireAuth />}>
        <Route path="/platform" element={<PlatformLayout />}>
          <Route index element={<p>Platform home</p>} />
        </Route>
      </Route>
    </Routes>,
    ["/platform"],
  )
  return fetchMock
}

describe("PlatformLayout", () => {
  it("shows people without the platform role the same not-found screen and calls nothing", async () => {
    const fetchMock = render(false)

    expect(await screen.findByText("Page not found")).toBeInTheDocument()
    expect(screen.queryByText("Platform home")).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.every(([url]) => String(url).endsWith("/auth/me"))).toBe(true)
  })

  it("shows administrators the platform navigation and content", async () => {
    render(true)

    expect(await screen.findByText("Platform home")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Organizations" })).toHaveAttribute("href", "/platform")
    expect(screen.getByRole("link", { name: "Platform audit" })).toHaveAttribute(
      "href",
      "/platform/audit",
    )
  })
})
