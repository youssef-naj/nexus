import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { ScrollRegion } from "./ScrollRegion"

describe("ScrollRegion", () => {
  it("is a named region that keyboard users can focus to scroll", () => {
    render(
      <ScrollRegion label="Requests">
        <table>
          <tbody>
            <tr>
              <td>wide content</td>
            </tr>
          </tbody>
        </table>
      </ScrollRegion>,
    )

    const region = screen.getByRole("region", { name: "Requests" })
    expect(region).toHaveAttribute("tabindex", "0")
    expect(region).toHaveClass("overflow-x-auto")
    expect(screen.getByText("wide content")).toBeInTheDocument()
  })
})
