import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { AppLayout } from "./AppLayout"

afterEach(() => vi.unstubAllGlobals())

describe("AppLayout", () => {
  it("signs the user out, sends the CSRF header and returns to the login page", async () => {
    const fetchMock = mockApi({
      "GET /auth/me": () =>
        jsonResponse(200, { id: "1", email: "ada@example.com", displayName: "Ada" }),
      "POST /auth/logout": () => jsonResponse(204),
    })
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<p>Login page</p>} />
        <Route element={<RequireAuth />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<p>Inside the app</p>} />
          </Route>
        </Route>
      </Routes>,
      ["/"],
    )

    expect(await screen.findByText("Inside the app")).toBeInTheDocument()
    expect(screen.getByText("Ada")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Sign out" }))

    expect(await screen.findByText("Login page")).toBeInTheDocument()
    const init = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/auth/logout"))?.[1]
    expect(init?.method).toBe("POST")
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })
})
