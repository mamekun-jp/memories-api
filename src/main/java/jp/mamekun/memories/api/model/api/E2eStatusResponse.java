package jp.mamekun.memories.api.model.api;

/**
 * Tells clients whether the server currently supports end-to-end encrypted messages.
 * When disabled (APP_E2E_ENABLED=false), clients should fall back to plaintext.
 */
public record E2eStatusResponse(boolean enabled) {
}
