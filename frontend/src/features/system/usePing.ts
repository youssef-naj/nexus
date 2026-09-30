import { useQuery } from "@tanstack/react-query"
import { fetchPing } from "./api"

export function usePing() {
  return useQuery({ queryKey: ["system", "ping"], queryFn: fetchPing })
}
