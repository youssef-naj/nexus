import { describe, expect, it } from "vitest"
import indexHtml from "../../index.html?raw"

describe("index.html", () => {
  it("keeps URLs that carry tokens out of referrer headers", () => {
    expect(indexHtml).toMatch(/<meta\s+name="referrer"\s+content="no-referrer"/)
  })

  it("declares a language and a viewport for accessibility and small screens", () => {
    expect(indexHtml).toMatch(/<html[^>]*\blang="[a-z-]+"/)
    expect(indexHtml).toMatch(/<meta\s+name="viewport"/)
  })
})
