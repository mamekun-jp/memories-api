# Push notifications — memories-api

Two complementary mechanisms:

1. **Native APNs pushes** sent by this backend when `APP_ENABLE_PUSH=true` (payload carries routing data).
2. **Background update check** — the app's `BGAppRefreshTask` (`jp.mamekun.background.updateChecker`) polls
   `GET /api/notification/check` and posts a *local* notification. This keeps working even when APNs is off.

## APNs setup (one-time)

1. Apple Developer portal → Keys → create an **Apple Push Notification service key (APNs)** → download the `.p8`.
   Note the **Key ID** and **Team ID**.
2. Base64-encode the `.p8` file content (single line, no headers):
   ```bash
   base64 -i AuthKey_XXXX.p8 | tr -d '\n'
   ```
3. Set the env vars (see `.env.sample` / `docs/TOKENS.md`):
   ```
   APP_ENABLE_PUSH=true
   APNS_KEY_ID=XXXX
   APNS_TEAM_ID=YYYY
   APNS_BUNDLE_ID=jp.mamekun.memories   # app bundle id, used as the APNs topic
   APNS_PRIVATE_KEY_BASE64=<base64 of .p8>
   APNS_PRODUCTION=false                  # true for App Store builds
   ```
4. The iOS app must have the Push Notification capability and `aps-environment` entitlement (already in
   `MemoriesApp.entitlements`); it registers its device token via `POST /api/users/me/update-token`.

## Payload structure

`PushNotificationServiceImpl` builds the APNs JSON from `PushMessage`:

```json
{
  "aps": {
    "alert": { "title": "...", "body": "..." },
    "sound": "default",
    "mutable-content": 1
  },
  "type": "NEW_COMMENT",
  "targetId": "<post UUID>",
  "actorId": "<user UUID>"
}
```

- Alert pushes use `PushType.ALERT` / `IMMEDIATE`.
- Silent pushes (`sendBackgroundPush`) send `{"aps":{"content-available":1}}` with
  `PushType.BACKGROUND` / `CONSERVE_POWER` to wake the app's background check.
- Invalid device tokens are nulled on the user (`UserService.nullifyToken`) when APNs reports an invalidation timestamp.

## Deep links on the app side

The app reads `userInfo["type"|"targetId"|"actorId"]` in `AppDelegate.didReceive` → `NotificationRouter` →
pushed on the feed navigation stack:

| `type` | route |
|---|---|
| `NEW_POST`, `NEW_COMMENT`, `NEW_LIKE` | single post view (`targetId`) |
| `NEW_STORY` | story viewer (`targetId`) |
| `NEW_MESSAGE` | messages inbox |
| `NEW_FOLLOWER` | actor's profile (`actorId`) |

Push bodies never contain message content — for messages the body is just "Sent you a message"
(see `MessageController`).

## Troubleshooting

- No pushes at all: check `APP_ENABLE_PUSH=true`, key/team/bundle id, `APNS_PRODUCTION` matches the build,
  and that the app re-registered its token after reinstall (`/users/me/update-token`).
- `ApnsConfig` fails at startup when push is enabled but `APNS_PRIVATE_KEY_BASE64` is missing/invalid —
  the bean is only created when `app.push-enabled=true`.
- Badge counts come from the app's background check (`/api/notification/check`), independent of APNs.
