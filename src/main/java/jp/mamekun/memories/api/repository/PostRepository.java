package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Post;
import jp.mamekun.memories.api.model.User;
import jp.mamekun.memories.api.model.api.PostDetailsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /*
     * post_type is stored as the enum ordinal: VIDEO=0, IMAGE=1, STORY_VIDEO=2, STORY_IMAGE=3.
     * Every query takes :viewerId so the result can say whether the viewer liked/saved the post
     * and hide posts from users the viewer has blocked.
     */
    String POST_DETAILS_SELECT = """
        SELECT
            CAST(p.id AS VARCHAR) AS postId,
            p.caption AS caption,
            p.image_url AS imageUrl,
            CAST(p.post_type AS VARCHAR) AS postType,
            CAST(p.timestamp AS VARCHAR) AS timestamp,
            CAST(u.id AS VARCHAR) AS ownerId,
            u.username AS username,
            u.profile_image_url AS profileImageUrl,
            (SELECT COUNT(*) FROM post_like l WHERE l.post_id = p.id) AS likeCount,
            (SELECT COUNT(*) FROM comment c WHERE c.post_id = p.id) AS commentCount,
            CASE WHEN EXISTS (SELECT 1 FROM post_like ml WHERE ml.post_id = p.id AND ml.user_id = :viewerId)
                THEN TRUE ELSE FALSE END AS liked,
            CASE WHEN EXISTS (SELECT 1 FROM saved_post sp WHERE sp.post_id = p.id AND sp.user_id = :viewerId)
                THEN TRUE ELSE FALSE END AS saved
        FROM post p
        JOIN app_user u ON p.owner_uid = u.id
        """;

    String VISIBLE_TO_VIEWER = """
        p.is_deleted = FALSE
        AND p.owner_uid NOT IN (SELECT b.blocked_user_id FROM blocked_user b WHERE b.user_id = :viewerId)
        """;

    String NOT_EXPIRED_STORY = " (p.post_type IS NULL OR p.post_type NOT IN (2, 3) OR p.timestamp > :storyCutoff) ";
    String REGULAR_POST = " (p.post_type IS NULL OR p.post_type NOT IN (2, 3)) ";
    String STORY_POST = " p.post_type IN (2, 3) ";

    /** Everything (posts + active stories), newest first. Used by older app versions. */
    @Query(value = POST_DETAILS_SELECT + " WHERE " + VISIBLE_TO_VIEWER + " AND " + NOT_EXPIRED_STORY
            + " ORDER BY p.timestamp DESC", nativeQuery = true)
    List<PostDetailsProjection> findFeed(@Param("viewerId") UUID viewerId,
                                         @Param("storyCutoff") ZonedDateTime storyCutoff);

    /** One page of regular posts (no stories), newest first. */
    @Query(value = POST_DETAILS_SELECT + " WHERE " + VISIBLE_TO_VIEWER + " AND " + REGULAR_POST
            + " ORDER BY p.timestamp DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<PostDetailsProjection> findFeedPage(@Param("viewerId") UUID viewerId,
                                             @Param("limit") int limit, @Param("offset") int offset);

    /** Active stories, oldest first so they play in the order they were posted. */
    @Query(value = POST_DETAILS_SELECT + " WHERE " + VISIBLE_TO_VIEWER + " AND " + STORY_POST
            + " AND p.timestamp > :storyCutoff ORDER BY p.timestamp ASC", nativeQuery = true)
    List<PostDetailsProjection> findActiveStories(@Param("viewerId") UUID viewerId,
                                                  @Param("storyCutoff") ZonedDateTime storyCutoff);

    @Query(value = POST_DETAILS_SELECT + " WHERE " + VISIBLE_TO_VIEWER + " AND " + NOT_EXPIRED_STORY
            + " AND p.owner_uid = :ownerId ORDER BY p.timestamp DESC", nativeQuery = true)
    List<PostDetailsProjection> findByOwner(@Param("viewerId") UUID viewerId, @Param("ownerId") UUID ownerId,
                                            @Param("storyCutoff") ZonedDateTime storyCutoff);

    @Query(value = POST_DETAILS_SELECT + " WHERE " + VISIBLE_TO_VIEWER + " AND p.id = :postId", nativeQuery = true)
    Optional<PostDetailsProjection> findDetailsById(@Param("viewerId") UUID viewerId, @Param("postId") UUID postId);

    @Query(value = POST_DETAILS_SELECT + " JOIN saved_post s ON s.post_id = p.id AND s.user_id = :viewerId"
            + " WHERE " + VISIBLE_TO_VIEWER + " ORDER BY s.timestamp DESC", nativeQuery = true)
    List<PostDetailsProjection> findSaved(@Param("viewerId") UUID viewerId);

    List<Post> findAllByOwner(User owner);

    Optional<Post> findPostByIdAndIsDeleted(UUID id, Boolean isDeleted);

    @Query(value = "SELECT COUNT(*) FROM post p WHERE p.owner_uid = :ownerId AND p.is_deleted = FALSE AND "
            + REGULAR_POST, nativeQuery = true)
    Integer countRegularPostsByOwner(@Param("ownerId") UUID ownerId);
}
