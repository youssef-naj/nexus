import { screen } from "@testing-library/react"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { mockApi, renderWithProviders } from "@/test/utils"
import { LoginPage } from "./LoginPage"

afterEach(() => vi.unstubAllGlobals())

describe("LoginPage when coming from an invitation link", () => {
  it("reminds the person which email address to use", () => {
    mockApi({})
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>,
      [{ pathname: "/login", state: { from: "/invitations/accept?token=abc" } }],
    )

    expect(
      screen.getByText(/Sign in with the email address your invitation was sent to/),
    ).toBeInTheDocument()
  })

  it("shows no such hint for an ordinary visit", () => {
    mockApi({})
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>,
      ["/login"],
    )

    expect(screen.queryByText(/invitation was sent to/)).not.toBeInTheDocument()
  })
})
