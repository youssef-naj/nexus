import { act, render } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router"
import { describe, expect, it } from "vitest"
import { RootLayout } from "./RootLayout"

function setup(initial: string) {
  const router = createMemoryRouter(
    [
      {
        element: <RootLayout />,
        children: [
          {
            path: "/",
            element: (
              <main id="main" tabIndex={-1}>
                Home page
              </main>
            ),
            handle: { title: "Home" },
          },
          {
            path: "/requests",
            element: (
              <main id="main" tabIndex={-1}>
                Requests page
              </main>
            ),
            handle: { title: "Requests" },
          },
          {
            path: "/untitled",
            element: (
              <main id="main" tabIndex={-1}>
                Untitled page
              </main>
            ),
          },
        ],
      },
    ],
    { initialEntries: [initial] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe("RootLayout", () => {
  it("sets the page title from the route", () => {
    setup("/requests")

    expect(document.title).toBe("Requests · Nexus")
  })

  it("falls back to the product name for a route without a title", () => {
    setup("/untitled")

    expect(document.title).toBe("Nexus")
  })

  it("updates the title when navigating", async () => {
    const router = setup("/")
    expect(document.title).toBe("Home · Nexus")

    await act(async () => {
      await router.navigate("/requests")
    })

    expect(document.title).toBe("Requests · Nexus")
  })

  it("does not steal focus on the first load", () => {
    setup("/")

    expect(document.getElementById("main")).not.toHaveFocus()
  })

  it("moves focus to the main region after navigating to another page", async () => {
    const router = setup("/")

    await act(async () => {
      await router.navigate("/requests")
    })

    expect(document.getElementById("main")).toHaveFocus()
  })

  it("leaves focus alone when only the query string changes", async () => {
    const router = setup("/requests")

    await act(async () => {
      await router.navigate("/requests?status=APPROVED")
    })

    expect(document.getElementById("main")).not.toHaveFocus()
  })
})
