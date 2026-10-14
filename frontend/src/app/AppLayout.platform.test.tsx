import { screen } from "@testing-library/react"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { AppLayout } from "./AppLayout"

afterEach(() => vi.unstubAllGlobals())

function render(platformAdmin: boolean) {
  mockApi({
    "GET /auth/me": () =>
      jsonResponse(200, { id: "1", email: "a@b.co", displayName: "Ada", platformAdmin }),
    "GET /orgs": () => jsonResponse(200, []),
  })
  renderWithProviders(
    <Routes>
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<p>Inside the app</p>} />
        </Route>
      </Route>
    </Routes>,
    ["/"],
  )
}

describe("AppLayout platform link", () => {
  it("shows the Platform link to platform administrators", async () => {
    render(true)

    expect(await screen.findByRole("link", { name: "Platform" })).toHaveAttribute(
      "href",
      "/platform",
    )
  })

  it("does not show it to anyone else", async () => {
    render(false)

    await screen.findByText("Inside the app")
    expect(screen.queryByRole("link", { name: "Platform" })).not.toBeInTheDocument()
  })
})
