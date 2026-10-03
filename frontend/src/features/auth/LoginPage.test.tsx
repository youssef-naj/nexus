import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { LoginPage } from "./LoginPage"

afterEach(() => vi.unstubAllGlobals())

function renderLogin() {
  renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<p>Dashboard</p>} />
    </Routes>,
    ["/login"],
  )
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText("Email"), email)
  await user.type(screen.getByLabelText("Password"), password)
  await user.click(screen.getByRole("button", { name: "Sign in" }))
}

describe("LoginPage", () => {
  it("shows validation messages and sends nothing when the form is empty", async () => {
    const fetchMock = mockApi({})
    renderLogin()

    await userEvent.setup().click(screen.getByRole("button", { name: "Sign in" }))

    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument()
    expect(screen.getByText("Enter your password.")).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("signs in with a form post, sends the CSRF header and opens the app", async () => {
    const fetchMock = mockApi({
      "POST /auth/login": () => jsonResponse(204),
      "GET /auth/me": () =>
        jsonResponse(200, { id: "1", email: "ada@example.com", displayName: "Ada" }),
    })
    renderLogin()

    await fillAndSubmit("ada@example.com", "s3cret-passphrase")

    expect(await screen.findByText("Dashboard")).toBeInTheDocument()
    const init = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/auth/login"))?.[1]
    expect(String(init?.body)).toBe("email=ada%40example.com&password=s3cret-passphrase")
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("shows a generic message for invalid credentials", async () => {
    mockApi({ "POST /auth/login": () => jsonResponse(401, { status: 401 }) })
    renderLogin()

    await fillAndSubmit("ada@example.com", "wrong-password")

    expect(await screen.findByText("Invalid email or password.")).toBeInTheDocument()
  })

  it("tells the user how long to wait when rate limited", async () => {
    mockApi({
      "POST /auth/login": () => jsonResponse(429, { status: 429 }, { "Retry-After": "900" }),
    })
    renderLogin()

    await fillAndSubmit("ada@example.com", "whatever-password")

    expect(await screen.findByText(/Try again in 15 minutes/)).toBeInTheDocument()
  })

  it("explains an unverified email only when the server says so", async () => {
    mockApi({
      "POST /auth/login": () =>
        jsonResponse(403, { status: 403, code: "EMAIL_NOT_VERIFIED", title: "Email not verified" }),
    })
    renderLogin()

    await fillAndSubmit("ada@example.com", "s3cret-passphrase")

    expect(await screen.findByText(/isn't verified yet/)).toBeInTheDocument()
  })
})
