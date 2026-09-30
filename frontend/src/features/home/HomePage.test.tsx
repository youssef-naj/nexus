import { screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { renderWithQuery } from "@/test/utils"
import { HomePage } from "./HomePage"

afterEach(() => vi.unstubAllGlobals())

describe("HomePage", () => {
  it("shows the product name and primary action", () => {
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})))
    renderWithQuery(<HomePage />)

    expect(screen.getByText("Nexus")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Primary action" })).toBeInTheDocument()
  })
})
