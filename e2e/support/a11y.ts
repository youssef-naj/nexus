import AxeBuilder from "@axe-core/playwright"
import { expect, test, type Page } from "@playwright/test"

const WCAG = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]

/**
 * Fails on any WCAG 2.x level A or AA violation. Axe's "best practice" findings are listed in the
 * report as annotations but do not fail the run.
 */
export async function expectAccessible(page: Page, label: string): Promise<void> {
  const wcag = await new AxeBuilder({ page }).withTags(WCAG).analyze()
  const summary = wcag.violations.map(
    (violation) =>
      `${violation.id} (${violation.impact ?? "n/a"}): ${violation.help}\n      ` +
      violation.nodes
        .slice(0, 3)
        .map((node) => node.target.map(String).join(" "))
        .join("\n      "),
  )
  expect(summary, `${label}: WCAG violations`).toEqual([])

  const advice = await new AxeBuilder({ page }).withTags(["best-practice"]).analyze()
  for (const violation of advice.violations) {
    test.info().annotations.push({
      type: `best practice: ${label}`,
      description: `${violation.id}: ${violation.help} (${violation.nodes.length} element(s))`,
    })
  }
}
