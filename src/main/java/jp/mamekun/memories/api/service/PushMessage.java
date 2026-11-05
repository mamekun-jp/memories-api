package jp.mamekun.memories.api.service;

/**
 * Structured native push message sent over APNs.
 *
 * <p>Alert pushes carry a title/body plus routing data ({@code type}, {@code targetId},
 * {@code actorId}) at the top level of the payload so the app can deep-link when the
 * notification is tapped. Background pushes carry no alert and only set
 * {@code content-available} so the app can run its background update check.
 */
public record PushMessage(
        String title,
        String body,
        String type,
        String targetId,
        String actorId,
        boolean background
) {

    public static PushMessage alert(String title, String body, String type, String targetId, String actorId) {
        return new PushMessage(title, body, type, targetId, actorId, false);
    }

    public static PushMessage silent() {
        return new PushMessage(null, null, null, null, null, true);
    }
}
