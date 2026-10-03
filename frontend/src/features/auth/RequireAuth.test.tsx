import { screen } from "@testing-library/react"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { RequireAuth } from "./RequireAuth"

afterEach(() => vi.unstubAllGlobals())

function renderGuarded() {
  renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>Login page</p>} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<p>Secret</p>} />
      </Route>
    </Routes>,
    ["/"],
  )
}

describe("RequireAuth", () => {
  it("redirects to the login page when there is no session", async () => {
    mockApi({ "GET /auth/me": () => jsonResponse(401, { status: 401 }) })
    renderGuarded()

    expect(await screen.findByText("Login page")).toBeInTheDocument()
    expect(screen.queryByText("Secret")).not.toBeInTheDocument()
  })

  it("shows the protected content for a signed-in user", async () => {
    mockApi({
      "GET /auth/me": () =>
        jsonResponse(200, { id: "1", email: "ada@example.com", displayName: "Ada" }),
    })
    renderGuarded()

    expect(await screen.findByText("Secret")).toBeInTheDocument()
  })

  it("shows an error with a retry button when the server is unreachable", async () => {
    mockApi({ "GET /auth/me": () => jsonResponse(500, { status: 500 }) })
    renderGuarded()

    expect(await screen.findByText("Can't reach the server")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument()
  })
})
