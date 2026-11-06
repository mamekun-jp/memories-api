# Memories API
Memories API is the backend source code for [MyMemoriesApp](https://apps.apple.com/us/app/mymemoriesapp/id6740176333).

Check the app at Apple Store: https://apps.apple.com/us/app/mymemoriesapp/id6740176333

[MyMemoriesApp](https://apps.apple.com/us/app/mymemoriesapp/id6740176333) is an iOS project which allows you to create closed/public server to share photos and videos within the audience of your choice.

[MyMemoriesApp](https://apps.apple.com/us/app/mymemoriesapp/id6740176333) source code will become available soon!

## Documentation

- [AGENTS.md](AGENTS.md) — working guide for agents/humans: build, test, config, secrets policy, layout.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — codebase map and request flows (auth, posts, messages, push, keys).
- [docs/PUSH_NOTIFICATIONS.md](docs/PUSH_NOTIFICATIONS.md) — APNs setup, push payload structure, deep links, troubleshooting.
- [docs/E2E_MESSAGING.md](docs/E2E_MESSAGING.md) — end-to-end encrypted messages and the `APP_E2E_ENABLED` kill switch.
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — Docker/compose, CI pipeline, release checklist, rollback.
- [docs/TOKENS.sample.md](docs/TOKENS.sample.md) — template for saving tokens locally: `cp docs/TOKENS.sample.md docs/TOKENS.md` (`docs/TOKENS.md` is gitignored — never commit real tokens).

## Build from source

Gradle 8.11.1 requires JDK 21 to run (it fails on newer JDKs) — CI pins Temurin 21 automatically:

```bash
./gradlew build        # compile + tests
./gradlew bootJar      # build/libs/memories-api-0.3.0-SNAPSHOT.jar
docker build .         # image (expects the jar from the previous step)
```

You may create your own server within a few steps by using Docker!

## Quick start with Docker Compose

The repository ships a `docker-compose.yml` and a sample env file. Copy the sample,
adjust the values (database, JWT secret, mail, APNs push keys, ...), then start:

```bash
cp .env.sample .env   # edit .env with your own values
docker compose up -d
```

All settings from `.env` are passed to the container as environment variables
(see `.env.sample` for the full list, including the `APNS_*` params used for native
push notifications). Data is kept in the `memories-img`, `memories-vid` and
`memories-db` volumes.

## Plain docker run

```bash
docker run -d \
  --name memories-api \
  --platform linux/amd64 \
  -p 9090:9090 \
  -v /data/memories-img:/data/memories-img \
  -v /data/memories-vid:/data/memories-vid \
  -v /data/memories-db:/data/memories-db \
  mamekunjp/memories-api
```

/data/memories-img is the place where images will be stored.
You may change the path on the left for your local path

/data/memories-vid is the place where videos will be stored.
You may change the path on the left for your local path

/data/memories-db is the place where H2 database file will be stored.
You may change the path on the left for your local path

For more options please check out `docker-compose.yml` and `.env.sample` in this repository (all values below can also be set via `.env`):

```yaml
services:
  memories-server:
    restart: unless-stopped
    container_name: memories-server
    ports:
      - 9090:9090
    image: mamekunjp/memories-api
    environment:
      # Database Profile (Switch between H2 and PostgreSQL)
      - SPRING_PROFILES_ACTIVE=h2  # Change to "postgres" to use PostgreSQL

      # Data sources settings
      #- DATASOURCE_URL=jdbc:postgresql://localhost
      #- DATASOURCE_USERNAME=memories
      #- DATASOURCE_PASSWORD=Passw0rd

      # Video settings
      - VIDEO_PATH=/data/memories-vid
      - VIDEO_THUMBNAIL_PATH=/data/memories-vid/th
      - VIDEO_ALLOWED_FORMATS=mp4

      # Image settings
      - IMAGE_PATH=/data/memories-img
      - IMAGE_THUMBNAIL_PATH=/data/memories-img/th
      - IMAGE_ALLOWED_FORMATS=jpg|jpeg|png|gif|bmp|webp
      - IMAGE_RESIZE_IMAGES=true
      - IMAGE_MAX_SIZE=2048
      - IMAGE_THUMB_SIZE=320

      # App settings
      - APP_BROADCAST_POSTS=true
    volumes:
      - type: bind
        source: /data/memories-img
        target: /data/memories-img
      - type: bind
        source: /data/memories-vid
        target: /data/memories-vid
      - type: bind
        source: /data/memories-db
        target: /data/memories-db
```

Server settings can be easily shared with the audience by using the URL format:
```
memories-app://addServer?name={SERVER_NAME}&address=https://{YOUR_SERVER_ADDRESS}&key={SERVER_KEY}
```

This will automatically configure the app to use the given server for the backend.

## Secrets

Never commit real tokens. Local values go in `.env` (gitignored) or `docs/TOKENS.md`
(copy `docs/TOKENS.sample.md`, gitignored). CI secrets live in GitHub Actions secrets
(`DOCKER_HUB_USERNAME` / `DOCKER_HUB_PASSWORD`). See `docs/TOKENS.sample.md` for the full registry.