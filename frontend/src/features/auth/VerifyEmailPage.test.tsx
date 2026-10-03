import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { VerifyEmailPage } from "./VerifyEmailPage"

afterEach(() => vi.unstubAllGlobals())

describe("VerifyEmailPage", () => {
  it("explains an incomplete link", () => {
    mockApi({})
    renderWithProviders(<VerifyEmailPage />, ["/verify-email"])

    expect(screen.getByText("Incomplete link")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Confirm email" })).not.toBeInTheDocument()
  })

  it("does nothing until the person clicks, then confirms", async () => {
    const fetchMock = mockApi({ "POST /auth/verify-email": () => jsonResponse(204) })
    renderWithProviders(<VerifyEmailPage />, ["/verify-email?token=abc123"])

    expect(fetchMock).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole("button", { name: "Confirm email" }))

    expect(await screen.findByText("Email verified")).toBeInTheDocument()
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({ token: "abc123" })
  })

  it("shows a clear message for an invalid or expired link", async () => {
    mockApi({ "POST /auth/verify-email": () => jsonResponse(400, { status: 400 }) })
    renderWithProviders(<VerifyEmailPage />, ["/verify-email?token=old"])

    await userEvent.setup().click(screen.getByRole("button", { name: "Confirm email" }))

    expect(await screen.findByText(/invalid or has expired/)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Confirm email" })).toBeInTheDocument()
  })
})
