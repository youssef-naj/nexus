import { test as base, expect, type Browser, type BrowserContext, type Page } from "@playwright/test"
import { BASE_URL, authFile, type Person } from "./session"

class Cast {
  private readonly contexts: BrowserContext[] = []

  constructor(private readonly browser: Browser) {}

  /** A page signed in as this person, in its own browser context. */
  async open(person: Person): Promise<Page> {
    const context = await this.browser.newContext({ baseURL: BASE_URL, storageState: authFile(person) })
    this.contexts.push(context)
    return context.newPage()
  }

  async close(): Promise<void> {
    await Promise.all(this.contexts.map((context) => context.close()))
  }
}

export const test = base.extend<{ cast: Cast }>({
  cast: async ({ browser }, use) => {
    const cast = new Cast(browser)
    await use(cast)
    await cast.close()
  },
})

export { expect }
