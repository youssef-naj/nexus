import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { RegisterPage } from "./RegisterPage"

afterEach(() => vi.unstubAllGlobals())

async function fill(name: string, email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText("Name"), name)
  await user.type(screen.getByLabelText("Email"), email)
  await user.type(screen.getByLabelText("Password"), password)
  await user.click(screen.getByRole("button", { name: "Create account" }))
}

describe("RegisterPage", () => {
  it("rejects a short password in the browser without calling the server", async () => {
    const fetchMock = mockApi({})
    renderWithProviders(<RegisterPage />)

    await fill("Ada", "ada@example.com", "short")

    expect(
      await screen.findByText("Password must be at least 12 characters long."),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("shows the same 'check your email' screen on success", async () => {
    const fetchMock = mockApi({
      "POST /auth/register": () => jsonResponse(202, { message: "Registration received." }),
    })
    renderWithProviders(<RegisterPage />)

    await fill("  Ada  ", "ada@example.com", "correct-horse-battery")

    expect(await screen.findByRole("heading", { name: "Check your email" })).toBeInTheDocument()
    const init = fetchMock.mock.calls[0]?.[1]
    expect(JSON.parse(String(init?.body))).toEqual({
      displayName: "Ada",
      email: "ada@example.com",
      password: "correct-horse-battery",
    })
  })

  it("shows server-side field errors under the right field", async () => {
    mockApi({
      "POST /auth/register": () =>
        jsonResponse(400, {
          status: 400,
          errors: { email: ["must be a well-formed email address"] },
        }),
    })
    renderWithProviders(<RegisterPage />)

    await fill("Ada", "ada@example.com", "correct-horse-battery")

    expect(await screen.findByText("must be a well-formed email address")).toBeInTheDocument()
    expect(screen.queryByRole("heading", { name: "Check your email" })).not.toBeInTheDocument()
  })
})
