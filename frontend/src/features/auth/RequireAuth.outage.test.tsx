import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MemoryRouter, Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { meQueryKey } from "./queries"
import { RequireAuth } from "./RequireAuth"

afterEach(() => vi.unstubAllGlobals())

const USER = { id: "1", email: "ada@example.com", displayName: "Ada", platformAdmin: false }

function routes() {
  return (
    <Routes>
      <Route path="/login" element={<p>Login page</p>} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<p>Inside the app</p>} />
      </Route>
    </Routes>
  )
}

describe("RequireAuth during outages", () => {
  it("does not send people to the sign-in page while the service is unavailable, and recovers on retry", async () => {
    let available = false
    mockApi({
      "GET /auth/me": () =>
        available ? jsonResponse(200, USER) : jsonResponse(503, { status: 503 }),
    })
    renderWithProviders(routes(), ["/"])

    expect(await screen.findByText("Temporarily unavailable")).toBeInTheDocument()
    expect(
      screen.getByText("The service is temporarily unavailable. Please try again in a moment."),
    ).toBeInTheDocument()
    expect(screen.queryByText("Login page")).not.toBeInTheDocument()

    available = true
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))

    expect(await screen.findByText("Inside the app")).toBeInTheDocument()
  })

  it("still sends people who are really signed out to the sign-in page", async () => {
    mockApi({ "GET /auth/me": () => jsonResponse(401, { status: 401 }) })
    renderWithProviders(routes(), ["/"])

    expect(await screen.findByText("Login page")).toBeInTheDocument()
    expect(screen.queryByText("Temporarily unavailable")).not.toBeInTheDocument()
  })

  it("keeps showing the app when a background refresh of the session fails", async () => {
    let down = false
    mockApi({
      "GET /auth/me": () => (down ? jsonResponse(503, { status: 503 }) : jsonResponse(200, USER)),
    })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/"]}>{routes()}</MemoryRouter>
      </QueryClientProvider>,
    )
    expect(await screen.findByText("Inside the app")).toBeInTheDocument()

    down = true
    await client.invalidateQueries({ queryKey: meQueryKey })

    await waitFor(() => expect(client.getQueryState(meQueryKey)?.status).toBe("error"))
    expect(screen.getByText("Inside the app")).toBeInTheDocument()
    expect(screen.queryByText("Temporarily unavailable")).not.toBeInTheDocument()
    expect(screen.queryByText("Can't reach the server")).not.toBeInTheDocument()
  })
})
