# Architecture — memories-api

Spring Boot 3.5.11 service (Java 21) backing the Memories iOS app. Private photo/video sharing
server; all endpoints except `/api/auth/**` require a JWT bearer token.

## Request flows

```
iOS app ──Bearer JWT──> JwtAuthenticationFilter ──> controller ──> service ──> repository ──> H2/Postgres
                                   │                                  │
                                   │                                  ├─ NotificationService ──> PushNotificationService ──> APNs
                                   │                                  └─ EmailService (password reset)
                                   └─ MediaStorageService ──> /data volumes (images/videos/thumbnails, ffmpeg in Docker image)
```

## Controllers (`controller/`)

| Base path | Controller | Purpose |
|---|---|---|
| `/api/auth` | `AuthController` | register, login, refresh, forgot/reset password |
| `/api/posts` | `PostsController` | create/feed/stories/like/save/view/report/comments; comment likes (`POST`/`DELETE /comments/{commentId}/like`); story highlights (`GET /stories/archive`, `POST`/`DELETE /{postId}/highlight`, `GET /user/{userId}/highlights`) |
| `/api/users` | `UserController` | profiles, follow, block, device token registration (`/users/me/update-token`), user search |
| `/api/message` | `MessageController` | direct messages, conversations inbox, `secured` (E2E) flag |
| `/api/notification` | `NotificationController` | in-app notifications + `/check` badge endpoint polled by the app's background task |
| `/api/key` | `KeyController` | E2E public-key upload (`POST /api/key`), fetch (`GET /api/key/user/{id}`), status (`GET /api/key/status`) |
| `/api/image`, `/api/video` | `ImageController`, `VideoController` | media upload + serving, thumbnails |

## Services (`service/`)

- `NotificationService` — persists a `Notification` row and, when `APP_ENABLE_PUSH=true`, sends a structured
  APNs push (`PushMessage`: alert + `type`/`targetId`/`actorId` for deep links; see `docs/PUSH_NOTIFICATIONS.md`).
- `PushNotificationService` — two beans selected by `APP_ENABLE_PUSH`: `impl/PushNotificationServiceImpl` (Pushy/APNs,
  configured by `config/ApnsConfig.java`) and `NoOpPushNotificationService`. Also supports silent background pushes
  (`sendBackgroundPush`, `content-available`) alongside the app's own background update check.
- `AccountService`, `UserService`, `EmailService`, `MediaStorageService` — account logic, user ops, mail, media files.

## Data model (`model/`)

`User`, `Post` (`highlighted` pins a story to the profile), `Comment`, `CommentLike`, `Like`, `SavedPost`, `Message` (has `secured`), `Notification`, `Follow`, `Block`,
`Complaint`, `StoryView`, `UserKey` (E2E public keys), `PasswordResetToken`. DTOs in `model/api/`.

## Adding features

1. New setting → `${ENV_VAR:default}` in `application.properties` + entry in `.env.sample` + Dockerfile comment if it is container-relevant.
2. New endpoint → controller + DTO + repository method; update this file and `FeatureFlowTests`.
3. New push type → extend `NotificationTypeEnum`, call `notificationService.notifyUser/notifyEveryone`, and make
   sure the app's `NotificationRouter` maps the type to a route.
4. Build with JDK 21 (`./gradlew build`), then `docker build .` to validate the image.
