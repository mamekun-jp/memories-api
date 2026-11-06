# AGENTS.md — memories-api

Working guide for agents (and humans) making changes to this repository.
Read this before editing. When in doubt, prefer small diffs that match existing style.

## Stack

- Java 21 (toolchain in `build.gradle.kts`), Spring Boot 3.5.11, Gradle wrapper **8.11.1**, JPA/Hibernate, H2 (default) or PostgreSQL (`SPRING_PROFILES_ACTIVE`), Pushy 0.15.4 (APNs), jjwt 0.13.0, Lombok.
- Entry point: `src/main/java/jp/mamekun/memories/api/MemoriesApplication.java`.
- Config: `src/main/resources/application.properties` — **every value is an env placeholder** `${ENV_VAR:default}`. Never hardcode secrets; add a new `${...}` placeholder instead and document it in `.env.sample`.

## Build / test / run (important)

- Gradle 8.11.1 **cannot run on JDK 24+** ("Unsupported class file major version"). Always build with JDK 21:
  ```bash
  ./gradlew build          # compile + tests (uses Java 21 toolchain)
  ./gradlew bootJar        # produces build/libs/memories-api-0.3.0-SNAPSHOT.jar
  java -jar build/libs/memories-api-0.3.0-SNAPSHOT.jar
  ```
- CI pins JDK 21 via `actions/setup-java` in `.github/workflows/docker-image.yml` — do not bump the Gradle wrapper or the runner JDK without checking compatibility.
- Tests: `src/test/java/.../FeatureFlowTests.java`, `MemoriesApplicationTests.java`. Run `./gradlew test`.
- Docker: `docker build .` (needs `./gradlew build` first — the Dockerfile copies the boot jar via `ARG JAR_FILE`); compose quick start: `cp .env.sample .env && docker compose up -d`.

## Configuration & secrets policy

- All runtime params come from environment variables (see `.env.sample` for the full list: `DATASOURCE_*`, `JWT_SECRET`, `MAIL_*`, `APNS_*`, `APP_ENABLE_PUSH`, `APP_E2E_ENABLED`, `IMAGE_*`, `VIDEO_*`, `SERVER_PORT`, ...).
- **Never commit secrets.** `.env` and `docs/TOKENS.md` are gitignored. To save tokens/keys for future sessions, copy `docs/TOKENS.sample.md` to `docs/TOKENS.md` and fill it in locally (APNs .p8 key, JWT secret, server key, mail creds, Docker Hub token, CI secret names, agent API tokens). Production secrets live in GitHub Actions secrets, not in the repo.
- The Docker image reads the same env vars; `Dockerfile` documents them in a comment block and sets volume-backed path defaults.

## Code layout

- `controller/` — REST endpoints under `/api/*`: `auth`, `posts`, `users`, `message`, `notification`, `key`, `image`, `video` (+ `PasswordResetPageController` serving the reset-password HTML page).
- `service/` — business logic. `PushNotificationService` has two impls chosen by `app.push-enabled` (`APP_ENABLE_PUSH`): `impl/PushNotificationServiceImpl` (real APNs via `ApnsConfig`) and `NoOpPushNotificationService`. `NotificationService` stores in-app notifications and sends the matching push — see `docs/PUSH_NOTIFICATIONS.md`.
- `model/` + `model/api/` — JPA entities and request/response DTOs. `repository/` — Spring Data JPA.
- E2E-encrypted direct messages: `KeyController` (public key upload/status), `MessageController` (`secured` flag on messages), kill switch `APP_E2E_ENABLED` — see `docs/E2E_MESSAGING.md`.
- Security: `config/SecurityConfig.java` — `/api/auth/**` is public, everything else needs the `Authorization: Bearer <jwt>` header handled by `helper/JwtAuthenticationFilter.java`.

## Conventions

- Lombok for entities/DTOs (`@Getter/@Setter/@Data`), `@Log4j2` for logging. Push failures are logged, never thrown into the request path.
- When adding an endpoint, add it to `docs/ARCHITECTURE.md` and a test to `FeatureFlowTests` if it's part of a user flow.
- Don't change `server.port` semantics; it is `${SERVER_PORT:9090}`.

## Docs

- `README.md` — quick start and links.
- `docs/ARCHITECTURE.md` — codebase map and request flows.
- `docs/PUSH_NOTIFICATIONS.md` — APNs setup, payload structure, deep links.
- `docs/E2E_MESSAGING.md` — encryption design and the on/off switch.
- `docs/DEPLOYMENT.md` — Docker, compose, CI pipeline, release checklist.
- `docs/TOKENS.sample.md` — template for saving tokens locally (`docs/TOKENS.md`, gitignored).
