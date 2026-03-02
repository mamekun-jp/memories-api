package jp.mamekun.memories.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PushNotificationService {
    CompletableFuture<String> sendPush(
            String deviceToken,
            String title,
            String body
    ) throws JsonProcessingException;
}
