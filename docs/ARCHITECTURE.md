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
| `/api/auth` | `AuthController` | register (optional `inviteToken`; on invite-only spaces `403` + `INVITE_REQUIRED`/`INVITE_INVALID`, admins exempt), login (failures: `404` + `USER_NOT_FOUND`, `401` + `WRONG_PASSWORD` in `error`), refresh, forgot/reset password |
| `/api/auth/join/{token}`, `/api/auth/invite/{token}` | `InvitePageController` | public invite page (opens the app via `memories-app://join?…` or sends to the App Store and copies the invite for the app's first launch) and invite check (`valid`, `spaceName`) |
| `/api/invites` | `InviteController` | space admins (`APP_ADMIN_EMAILS`) create (`expiresInHours`, 1–720), list and revoke (`DELETE /{token}`) single-use invites |
| `/api/posts` | `PostsController` | create/feed/stories/like/save/view/report/comments; comment likes (`POST`/`DELETE /comments/{commentId}/like`); story highlights (`GET /stories/archive`, `POST`/`DELETE /{postId}/highlight`, `GET /user/{userId}/highlights`) |
| `/api/users` | `UserController` | profiles (a new `profileImageUrl` must be the user's own upload), follow, block, device token registration (`/users/me/update-token`), user search, settings (`GET`/`PUT /users/me/settings`: `readReceipts`, plus `isAdmin`/`spaceName`/`inviteOnly`) |
| `/api/message` | `MessageController` | direct messages (not to yourself), conversations inbox, `secured` (E2E) flag; `isRead` in threads is only shared when both people have `readReceipts` on; delete one (`DELETE /{messageId}`) or many (`POST /delete` with `ids`): the sender's delete removes it for both, the receiver's only hides it for them (`deletedByReceiver`) |
| `/api/groups` | `GroupChatController` | group chats: create (`name`, `memberIds`), list with last message/unread, rename (any member, `PUT /{id}`), add members (admins, `POST /{id}/members`), remove/leave (`DELETE /{id}/members/{userId}`: admins remove members, only the owner removes admins, nobody removes the owner), make/unmake admin (owner only, `PUT /{id}/members/{userId}/admin`), messages (`GET`/`POST /{id}/messages`, own-message delete via `POST /{id}/messages/delete`). Members see messages from when they joined; notices (`event`: CREATED, ADDED, ...) are part of the timeline. E2E messages carry one ciphertext per member (`copies`, stored as `GroupMessageCopy`); 409 when the copies don't match the current members. The owner (`createdBy`) passes to the oldest admin/member when they leave; the last one out deletes the group. Pushes `NEW_GROUP_MESSAGE` (targetId = group id) without an in-app notification |
| `/api/notification` | `NotificationController` | in-app notifications + `/check` badge endpoint polled by the app's background task |
| `/api/key` | `KeyController` | E2E public-key upload (`POST /api/key`), fetch (`GET /api/key/user/{id}`), status (`GET /api/key/status`) |
| `/api/image`, `/api/video` | `ImageController`, `VideoController` | media upload + serving, thumbnails; each upload is recorded with its uploader (`MediaUpload`) |

## Services (`service/`)

- `NotificationService` — persists a `Notification` row and, when `APP_ENABLE_PUSH=true`, sends a structured
  APNs push (`PushMessage`: alert + `type`/`targetId`/`actorId` for deep links; see `docs/PUSH_NOTIFICATIONS.md`).
- `PushNotificationService` — two beans selected by `APP_ENABLE_PUSH`: `impl/PushNotificationServiceImpl` (Pushy/APNs,
  configured by `config/ApnsConfig.java`) and `NoOpPushNotificationService`. Also supports silent background pushes
  (`sendBackgroundPush`, `content-available`) alongside the app's own background update check.
- `AccountService`, `UserService`, `EmailService`, `MediaStorageService` — account logic, user ops, mail, media files.
- `MediaOwnershipService` — who uploaded each media file: posts and profile photos may only use your own uploads
  (legacy files without a record can be claimed only if nobody else uses them), and a file is deleted from disk only
  when its owner's last post/profile using it is gone.
- `config/SpaceSettings` — `APP_SPACE_NAME`, `APP_ADMIN_EMAILS`, `APP_INVITE_ONLY`, `APP_STORE_URL`.

## Data model (`model/`)

`User`, `Post` (`highlighted` pins a story to the profile), `Comment`, `CommentLike`, `Like`, `SavedPost`, `Message` (has `secured`, `deletedByReceiver`), `Notification`, `Follow`, `Block`,
`Complaint`, `StoryView`, `UserKey` (E2E public keys), `PasswordResetToken`, `MediaUpload` (uploader of each file),
`Invite` (single-use, expiring; `User.readReceipts` null = on). DTOs in `model/api/`.

## Adding features

1. New setting → `${ENV_VAR:default}` in `application.properties` + entry in `.env.sample` + Dockerfile comment if it is container-relevant.
2. New endpoint → controller + DTO + repository method; update this file and `FeatureFlowTests` (or `SpaceFeaturesTests` for invite-only/admin flows).
3. New push type → extend `NotificationTypeEnum`, call `notificationService.notifyUser/notifyEveryone`, and make
   sure the app's `NotificationRouter` maps the type to a route.
4. Build with JDK 21 (`./gradlew build`), then `docker build .` to validate the image.
