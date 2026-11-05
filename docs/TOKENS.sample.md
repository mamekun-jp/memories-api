# TOKENS — local secret registry (template)

Copy this file to `docs/TOKENS.md` and fill in real values for the environment you are working on:

```bash
cp docs/TOKENS.sample.md docs/TOKENS.md
```

`docs/TOKENS.md` is **gitignored** — never commit real tokens. Production/CI secrets live in GitHub
Actions secrets, not in this repo.

## Backend server (memories-api)

| Token / secret | Used by | Stored as (env var / place) | Value |
|---|---|---|---|
| JWT signing secret | `app.jwt.secret` | `JWT_SECRET` in `.env` | |
| APNs auth key (.p8) | APNs pushes | `APNS_PRIVATE_KEY_BASE64` (base64 of .p8) | |
| APNs Key ID | APNs pushes | `APNS_KEY_ID` | |
| APNs Team ID | APNs pushes | `APNS_TEAM_ID` | |
| App bundle id (APNs topic) | APNs pushes | `APNS_BUNDLE_ID` = `jp.mamekun.memories` | |
| Server key (app registration) | `SignupRequest.serverKey` | server-side config / app deep link `memories-app://addServer?...&key=` | |
| Mail SMTP credentials | password reset mails | `MAIL_HOST/PORT/USERNAME/PASSWORD` | |
| DB credentials (postgres profile) | datasource | `DATASOURCE_USERNAME/PASSWORD` | |

## Docker Hub / CI

| Token | Used by | Stored as |
|---|---|---|
| Docker Hub username | CI push | GitHub secret `DOCKER_HUB_USERNAME` |
| Docker Hub access token | CI push | GitHub secret `DOCKER_HUB_PASSWORD` |

## Apple

| Token | Used by | Stored as |
|---|---|---|
| Apple ID / App Store Connect API key | releasing the iOS app | App Store Connect; keep the `.p8` in a password manager, not in git |

## Agent / automation tokens (optional)

| Token | Used for | Stored as |
|---|---|---|
| (e.g. LLM provider API key for coding agents) | agent tooling | `docs/TOKENS.md` only, or the agent's own env — never committed |

## Rules for agents

1. Never write a real token into a tracked file. Use `.env` (gitignored) or `docs/TOKENS.md` (gitignored).
2. When adding a new secret, add it to `.env.sample` (name + description only) and to the table above.
3. `git status` must stay clean of `.env` / `docs/TOKENS.md` before committing.
