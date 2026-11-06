# Deployment — memories-api

## Local run

```bash
cp .env.sample .env          # edit values
docker compose up -d         # published image + named volumes (memories-img/-vid/-db)
# or from source:
./gradlew build              # ALWAYS with JDK 21 (Gradle 8.11.1 breaks on newer JDKs)
docker compose up -d --build # uses "build: ." — uncomment it in docker-compose.yml
```

Volumes: `memories-img` → `/data/memories-img`, `memories-vid` → `/data/memories-vid`,
`memories-db` → `/data/memories-db` (H2 file). Port: `9090` (host port from `${SERVER_PORT:-9090}`).

## CI pipeline (`.github/workflows/docker-image.yml`)

On push/PR to `main`:

1. `actions/setup-java@v4` pins **Temurin JDK 21** (required — see AGENTS.md).
2. `gradle/gradle-build-action@v2` + `./gradlew build` (compile + tests).
3. Sanity `docker build`, then `docker/build-push-action` pushes `mamekunjp/memories-api:latest` and
   `mamekunjp/memories-api:<short-sha>` to Docker Hub (creds in GitHub secrets `DOCKER_HUB_USERNAME` /
   `DOCKER_HUB_PASSWORD`).
4. Verifies with `docker pull`.

## Release checklist

1. `./gradlew build` green on JDK 21 (locally and in CI).
2. `.env.sample` documents every new env var; `docs/TOKENS.md` (local, gitignored) has the real values for the target environment.
3. If the jar name/version changes, update `ARG JAR_FILE` in the `Dockerfile`.
4. Merge to `main` → CI builds and pushes the image.
5. On the server: `docker compose pull && docker compose up -d` (env from `.env`).
6. Smoke test: `curl https://<host>/api/notification/check` with a token → 200; app login + send a message +
   check `GET /api/key/status`.

## Rollback

`docker compose down` + edit `.env` pin (e.g. pull previous tag: change `image:` to `mamekunjp/memories-api:<sha>`)
+ `docker compose up -d`. H2 data persists in the `memories-db` volume; back it up before big migrations.

## Operational switches

| Env var | Effect |
|---|---|
| `APP_ENABLE_PUSH=false` | No APNs pushes (app still uses its background check) |
| `APP_E2E_ENABLED=false` | Rejects key uploads (410) and secured messages (400) → clients fall back to plaintext |
| `APP_BROADCAST_POSTS=false` | New posts/stories don't notify everyone |
| `SPRING_PROFILES_ACTIVE=postgres` | Use PostgreSQL (`DATASOURCE_URL/USERNAME/PASSWORD`) |
| `SQL_LOGGING_LEVEL=WARN` / `SQL_TYPE_DESCRIPTOR=WARN` | Silence Hibernate SQL / bound-parameter logs (levels: OFF..TRACE) |
| `JPA_SHOW_SQL=false` (default) | Keep `show-sql` off — it prints SQL to stdout ignoring log levels |
