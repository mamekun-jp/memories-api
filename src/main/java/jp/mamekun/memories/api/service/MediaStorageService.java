package jp.mamekun.memories.api.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Removes uploaded media files (and their thumbnails) from disk. */
@Service
@Log4j2
public class MediaStorageService {
    @Value("${image.path:/data/memories-img}")
    private String imageDir;

    @Value("${image.thumbnail-path:/data/memories-img/th}")
    private String imageThumbDir;

    @Value("${video.path:/data/memories-vid}")
    private String videoDir;

    @Value("${video.thumbnail-path:/data/memories-vid/th}")
    private String videoThumbDir;

    /**
     * @param mediaUrl the relative url stored on a post, e.g. {@code image/abc.jpg} or {@code video/abc.mp4}
     */
    public void deleteMedia(String mediaUrl) {
        if (mediaUrl == null || !mediaUrl.contains("/")) {
            return;
        }
        String filename = Paths.get(mediaUrl).getFileName().toString();
        if (mediaUrl.startsWith("video/")) {
            deleteQuietly(videoDir + "/" + filename);
            deleteQuietly(videoThumbDir + "/" + filename.replace(".mp4", ".jpg"));
        } else {
            deleteQuietly(imageDir + "/" + filename);
            deleteQuietly(imageThumbDir + "/" + filename);
        }
    }

    private void deleteQuietly(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.error("Failed to delete media file {}", path, e);
        }
    }
}
