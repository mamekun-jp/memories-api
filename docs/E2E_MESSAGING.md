# End-to-end encrypted direct messages

Direct messages can be E2E-encrypted on the device; the server only ever stores base64 ciphertext
and a `secured` flag.

## How it works

- **Keys (app, `CryptoHelper.swift`)**: each device generates a P-256 ECDH key pair. The private key stays in
  the iOS Keychain (`jp.mamekun.memories`); the public key is uploaded to `POST /api/key` (upsert — re-upload
  rotates the key) and can be fetched via `GET /api/key/user/{userId}`.
- **Encryption**: ECDH shared secret between sender private key and recipient public key →
  `x963DerivedSymmetricKey` (SHA-256, 32 bytes, sharedInfo = both public keys sorted) → AES-256-GCM.
  The sealed box is base64-encoded and sent as `content` with `secured: true`.
- **Decryption**: the recipient derives the same key from their private key + the sender's public key.
  Both thread directions work with the same key material.
- **Server**: `Message.secured` flag, `MessageRequest.secured`, `MessageResponse.secured`. Content column is
  `varchar(8192)` to fit ciphertext. Push notifications never include message content.

## On/off switch and fallback (server-controlled)

`APP_E2E_ENABLED` (default `true`) is the server-side kill switch:

- `GET /api/key/status` → `{"enabled": bool}` — the app checks this (cached 5 min) before encrypting.
- When disabled: `POST /api/key` → **410 Gone**, and `POST /api/message` with `secured:true` → **400**,
  so clients fall back to plaintext (`MessageService.sendMessage` retries plaintext on 400/410).
- When the recipient has **no public key** (old app version that never uploaded one), the sender also
  falls back to plaintext automatically.
- Any failure of the status check (e.g. an old server without `/key/status`) is treated as "disabled".

So: flip `APP_E2E_ENABLED=false` in `.env` and redeploy → new messages go plaintext; already-stored
secured messages stay encrypted and readable. Toggling only affects **new** messages.

## Testing

- Unit-ish: register two users, upload a key for one (`POST /api/key`), send `secured:true` message →
  expect 201 and `"secured":true` in `GET /api/message/with/{id}`; with `APP_E2E_ENABLED=false` expect 400/410.
- On device: both users on the new app version → bubbles show a lock icon; inbox preview shows
  "Encrypted message". Delete one user's key from the server → next message is plaintext.
