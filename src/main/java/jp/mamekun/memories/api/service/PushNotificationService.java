package jp.mamekun.memories.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.concurrent.CompletableFuture;

public interface PushNotificationService {
    CompletableFuture<String> sendPush(
            String deviceToken,
            String title,
            String body
    ) throws JsonProcessingException;

    /**
     * Sends a structured native push. Alert messages include routing data
     * ({@code type}, {@code targetId}, {@code actorId}) alongside the alert so the
     * app can deep-link on tap; background messages only carry {@code content-available}.
     */
    CompletableFuture<String> sendPush(
            String deviceToken,
            PushMessage message
    ) throws JsonProcessingException;

    /**
     * Sends a silent background push that wakes the app so it can run its
     * background update check (badge + local notification).
     */
    CompletableFuture<String> sendBackgroundPush(
            String deviceToken
    ) throws JsonProcessingException;
}
