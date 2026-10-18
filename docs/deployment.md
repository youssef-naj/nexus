# Deployment

The deployable stack is `compose.prod.yaml`: PostgreSQL, the backend and the web server (nginx, which also serves the single-page app). Reasons for each choice are in [ADR-0034](decisions/0034-deployment.md).

```
browser ──HTTPS──> TLS proxy (yours) ──> web (nginx, 127.0.0.1:8080) ──> backend ──> postgres
                                                                          │
                                                                          └──> your SMTP server
```

The backend and the database publish no ports. The database is on an internal network that only the backend shares.

## Requirements

A Linux host (or any machine) with Docker and Docker Compose 2.17 or newer, a DNS name, a TLS proxy in front, and an SMTP account for outgoing email.

## Configure

```bash
cp .env.prod.example .env.prod
```

Fill in every value. Compose refuses to start when a required one is missing.

| Variable | Meaning |
|---|---|
| `POSTGRES_PASSWORD` | Database password. Generate a long random one. |
| `POSTGRES_DB`, `POSTGRES_USER` | Database name and user (default `nexus`). |
| `NEXUS_PUBLIC_BASE_URL` | The public address, `https://...`. It is put into the links inside emails. |
| `NEXUS_MAIL_FROM` | Sender address of outgoing email. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH` | Your SMTP provider (STARTTLS on port 587 by default). |
| `NEXUS_HTTP_BIND`, `NEXUS_HTTP_PORT` | Where the web container listens on the host (default `127.0.0.1:8080`). Keep it on loopback and put a TLS proxy in front. |
| `NEXUS_PLATFORM_ADMIN_EMAILS` | Comma-separated verified accounts that become platform administrators at startup. Leave empty for none. |
| `NEXUS_SECURE_COOKIES` | Leave unset. Cookies are `Secure` by default; only the plain-HTTP demo switches this off. |

Never commit `.env.prod`.

## Start

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build --wait
```

The backend applies database migrations when it starts. `--wait` returns when the database, backend and web server are healthy.

## TLS in front

Anything that terminates TLS and forwards to `127.0.0.1:8080` works. With Caddy (automatic certificates):

```
nexus.example.com {
    reverse_proxy 127.0.0.1:8080
    header Strict-Transport-Security "max-age=31536000; includeSubDomains"
}
```

HSTS is sent by this layer, not by the application. The web container takes the client address from the proxy's `X-Forwarded-For` only when the connection comes from a private network, so per-IP rate limits see real clients.

## First administrator

Register through the application and verify the email. Add that address to `NEXUS_PLATFORM_ADMIN_EMAILS`, then restart the backend:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d backend
```

The role is granted at startup. Removing an address and restarting revokes it.

## Check that it works

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://nexus.example.com/healthz        # 200
curl -s -o /dev/null -w "%{http_code}\n" https://nexus.example.com/api/actuator/health   # 404: not exposed
docker compose --env-file .env.prod -f compose.prod.yaml ps                         # all healthy
```

Also confirm the response headers (`Content-Security-Policy`, `Strict-Transport-Security`) and that the browser console shows no policy violations.

## Operate

- **Logs:** `docker compose ... logs -f backend`. Logs contain no personal data. An error shown to a user carries a short incident reference; search the log for it.
- **Update:** `git pull`, then `docker compose --env-file .env.prod -f compose.prod.yaml up -d --build --wait`. Migrations are forward-only and run on start.
- **Backup:**

```bash
  docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > nexus-$(date +%F).dump
```

- **Restore** into an empty database:

```bash
  docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres \
    sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < nexus-2026-10-18.dump
```

Practice a restore before you need one. Backups are not automated.
- **Outages:** while the database is down the application answers `503` with `Retry-After`, readiness reports down, and it recovers without a restart.
- **Stop:** `docker compose ... down` keeps the data; `down -v` deletes the database volume.

## What this stack does not do

No automated backups, image vulnerability scanning, log aggregation or alerting; one instance only (rate limits are in memory); TLS, HSTS and exposure are yours to configure. See [status.md](status.md).
