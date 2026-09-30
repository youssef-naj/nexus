import { z } from "zod"
import { apiFetch } from "@/shared/api/client"

const pingSchema = z.object({
  status: z.string(),
  serverTime: z.iso.datetime(),
})

export type Ping = z.infer<typeof pingSchema>

export async function fetchPing(): Promise<Ping> {
  // Validate at the boundary: never trust the shape of a network response.
  return pingSchema.parse(await apiFetch<unknown>("/system/ping"))
}
