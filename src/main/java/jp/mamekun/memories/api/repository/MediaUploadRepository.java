package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.MediaUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {
    Optional<MediaUpload> findByUrl(String url);

    void deleteByUrl(String url);
}
