package jp.mamekun.memories.api.service.impl;

import jakarta.transaction.Transactional;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.repository.UserRepository;
import jp.mamekun.memories.api.service.UserService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> getTokenByUserId(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            String deviceToken = userOpt.get().getDeviceToken();
            return deviceToken == null ? Optional.empty() : Optional.of(deviceToken);
        } else {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public boolean nullifyToken(String token) {
        Optional<User> userOpt = userRepository.findUserByDeviceToken(token);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setDeviceToken(null);
            userRepository.save(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<String> findAllDeviceTokensStringList() {
        return userRepository.findAllDeviceTokenList();
    }
}
