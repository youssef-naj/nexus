import { z } from "zod"

/** The server's page envelope around a list of items. */
export function pageOf<T extends z.ZodType>(item: T) {
  return z.object({
    content: z.array(item),
    page: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  })
}
