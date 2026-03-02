package jp.mamekun.memories.api.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {
    Optional<String> getTokenByUserId(UUID userId);
    boolean nullifyToken(String token);
    List<String> findAllDeviceTokensStringList();
}
