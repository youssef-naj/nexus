import { describe, expect, it } from "vitest"
import conf from "../../nginx/default.conf?raw"
import headers from "../../nginx/security-headers.conf?raw"

const csp = /add_header Content-Security-Policy "([^"]+)"/.exec(headers)?.[1] ?? ""

function directive(name: string): string[] {
  const found = csp
    .split(";")
    .map((part) => part.trim())
    .find((part) => part === name || part.startsWith(`${name} `))
  return found ? found.split(/\s+/).slice(1) : []
}

describe("security headers", () => {
  it("only allows scripts from our own origin: no inline code, no eval", () => {
    expect(directive("script-src")).toEqual(["'self'"])
    expect(csp).not.toContain("unsafe-eval")
    expect(csp).not.toContain("script-src-elem")
  })

  it("locks down everything else it can", () => {
    expect(directive("default-src")).toEqual(["'self'"])
    expect(directive("connect-src")).toEqual(["'self'"])
    expect(directive("object-src")).toEqual(["'none'"])
    expect(directive("frame-ancestors")).toEqual(["'none'"])
    expect(directive("base-uri")).toEqual(["'self'"])
    expect(directive("form-action")).toEqual(["'self'"])
  })

  it("sends the other protective headers on every response, errors included", () => {
    for (const name of [
      "Referrer-Policy",
      "X-Content-Type-Options",
      "X-Frame-Options",
      "Permissions-Policy",
      "Cross-Origin-Opener-Policy",
      "Cross-Origin-Resource-Policy",
    ]) {
      expect(headers, name).toMatch(new RegExp(`add_header ${name} "[^"]+" always;`))
    }
    expect(headers).toContain('add_header Referrer-Policy "no-referrer" always;')
  })
})

describe("reverse proxy configuration", () => {
  it("does not expose the health endpoints and hides the server version", () => {
    expect(conf).toMatch(/location \^~ \/api\/actuator \{\s*return 404;\s*\}/)
    expect(conf).toContain("server_tokens off;")
  })

  it("proxies the API to the backend with a bounded request size and timeouts", () => {
    expect(conf).toContain("location /api/ {")
    expect(conf).toContain("client_max_body_size 128k;")
    expect(conf).toContain("set $backend http://backend:8080;")
    expect(conf).toMatch(/proxy_connect_timeout 5s;/)
    expect(conf).toMatch(/proxy_read_timeout 30s;/)
  })

  it("overwrites the forwarded address instead of appending to what the client sent", () => {
    expect(conf).toContain("proxy_set_header X-Forwarded-For $remote_addr;")
    expect(conf).not.toContain("$proxy_add_x_forwarded_for")
  })

  it("trusts forwarded client addresses from private networks only", () => {
    const trusted = [...conf.matchAll(/set_real_ip_from ([0-9./]+);/g)].map((match) => match[1])
    expect(trusted).toEqual(["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"])
  })

  it("serves the app with the policy, never caches the page and caches hashed files for a year", () => {
    const includes = conf.match(/include \/etc\/nginx\/snippets\/security-headers\.conf;/g) ?? []
    expect(includes).toHaveLength(2)
    expect(conf).toMatch(
      /location \/ \{[^}]*Cache-Control "no-store" always;[^}]*try_files \$uri \/index\.html;/,
    )
    expect(conf).toMatch(/location \/assets\/ \{[^}]*immutable[^}]*try_files \$uri =404;/)
  })

  it("never compresses API responses", () => {
    expect(conf).not.toMatch(/gzip_types[^;]*application\/json/)
  })
})
