package jp.mamekun.memories.api.service;

import jp.mamekun.memories.api.model.MediaUpload;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.repository.MediaUploadRepository;
import jp.mamekun.memories.api.repository.PostRepository;
import jp.mamekun.memories.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps track of who uploaded each media file. Posts and profile photos may only use files
 * the person uploaded themselves, and a file is only removed from disk when nobody else uses it.
 */
@Service
@RequiredArgsConstructor
public class MediaOwnershipService {
    /** What the upload endpoints hand out: {@code image/<name>.<ext>} or {@code video/<name>.<ext>}. */
    private static final Pattern MEDIA_URL = Pattern.compile("^(image|video)/[A-Za-z0-9_-]+\\.[A-Za-z0-9]+$");

    private final MediaUploadRepository mediaUploadRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /** Called by the upload endpoints right after a file is stored. */
    public void recordUpload(String url, User owner) {
        mediaUploadRepository.save(new MediaUpload(url, owner.getId(), ZonedDateTime.now()));
    }

    /**
     * Whether {@code user} may attach this media file to a post or use it as a profile photo.
     * Files uploaded before ownership was tracked have no record: they can be used only when
     * no one else is using them yet, and they then become the user's.
     */
    public boolean mayUse(String url, User user) {
        if (url == null || url.isBlank()) {
            return true;
        }
        if (!MEDIA_URL.matcher(url).matches()) {
            return false;
        }
        Optional<MediaUpload> upload = mediaUploadRepository.findByUrl(url);
        if (upload.isPresent()) {
            return upload.get().getOwnerId().equals(user.getId());
        }
        if (postRepository.existsByImageUrlAndOwner_IdNot(url, user.getId())
                || userRepository.existsByProfileImageUrlAndIdNot(url, user.getId())) {
            return false;
        }
        recordUpload(url, user);
        return true;
    }

    /**
     * Whether the file used by the post {@code postId} can be removed from disk when that post is
     * deleted: only when it belongs to the post's owner and nothing else uses it.
     * Call it before the post itself is deleted.
     */
    public boolean mayDeletePostFile(String url, UUID ownerId, UUID postId) {
        if (!isOwnedOrUntracked(url, ownerId)) {
            return false;
        }
        return !postRepository.existsByImageUrlAndIdNot(url, postId)
                && !userRepository.existsByProfileImageUrl(url);
    }

    /**
     * Whether a user's profile photo file can be removed from disk when they stop using it:
     * only when it is theirs and no post or other profile uses it.
     */
    public boolean mayDeleteProfileFile(String url, UUID ownerId) {
        if (!isOwnedOrUntracked(url, ownerId)) {
            return false;
        }
        return !postRepository.existsByImageUrl(url)
                && !userRepository.existsByProfileImageUrlAndIdNot(url, ownerId);
    }

    /** Forgets the ownership record of a file that was removed from disk. */
    public void forget(String url) {
        mediaUploadRepository.findByUrl(url).ifPresent(mediaUploadRepository::delete);
    }

    private boolean isOwnedOrUntracked(String url, UUID ownerId) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return mediaUploadRepository.findByUrl(url)
                .map(upload -> upload.getOwnerId().equals(ownerId))
                .orElse(true);
    }
}
