package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Post;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.PostDetailsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findAllByIsDeletedOrderByTimestampDesc(Boolean isDeleted);

    @Query(value = """
    SELECT 
        CAST(p.id AS VARCHAR) AS postId,
        p.caption AS caption,
        p.image_url AS imageUrl,
        CAST(p.post_type AS VARCHAR) AS postType,
        CAST(p.timestamp AS VARCHAR) AS timestamp,
        CAST(u.id AS VARCHAR) AS ownerId,
        u.username AS username,
        u.profile_image_url AS profileImageUrl,
        COALESCE(l.like_count, 0) AS likeCount,
        COALESCE(c.comment_count, 0) AS commentCount
    FROM 
        post p
    LEFT JOIN 
        app_user u ON p.owner_uid = u.id
    LEFT JOIN 
        (SELECT post_id, COUNT(*) AS like_count 
         FROM post_like 
         GROUP BY post_id) l 
        ON p.id = l.post_id
    LEFT JOIN 
        (SELECT post_id, COUNT(*) AS comment_count 
         FROM comment 
         GROUP BY post_id) c 
        ON p.id = c.post_id
    WHERE 
        p.is_deleted = :isDeleted
    ORDER BY 
        p.timestamp DESC
    """, nativeQuery = true)
    List<PostDetailsProjection> findPostsWithDetailsByIsDeleted(Boolean isDeleted);

    List<Post> findAllByOwnerAndIsDeletedOrderByTimestampDesc(User owner, Boolean isDeleted);

    void deleteByOwner(User owner);

    Optional<Post> findPostByIdAndIsDeleted(UUID id, Boolean isDeleted);

    Integer countByOwner_Id(UUID ownerId);
}
