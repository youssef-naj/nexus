import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterEach, beforeEach } from "vitest"

beforeEach(() => {
  // A browser that already has the CSRF cookie, so tests only see the requests they care about
  document.cookie = "XSRF-TOKEN=test-token; path=/"
})

afterEach(() => cleanup())
