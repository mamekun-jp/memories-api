package jp.mamekun.memories.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.mamekun.memories.api.model.Post;
import jp.mamekun.memories.api.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks through the social features end to end against an in-memory H2 database.
 * To run against PostgreSQL instead, set TEST_DATASOURCE_URL, TEST_DATASOURCE_DRIVER=org.postgresql.Driver,
 * TEST_DATASOURCE_DIALECT=org.hibernate.dialect.PostgreSQLDialect, TEST_DATASOURCE_USERNAME and
 * TEST_DATASOURCE_PASSWORD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DATASOURCE_URL:jdbc:h2:mem:feature-flow;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driverClassName=${TEST_DATASOURCE_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${TEST_DATASOURCE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:password}",
        "spring.jpa.database-platform=${TEST_DATASOURCE_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.type.descriptor.sql=WARN",
        "image.path=${java.io.tmpdir}/memories-test-img",
        "image.thumbnail-path=${java.io.tmpdir}/memories-test-img/th",
        "video.path=${java.io.tmpdir}/memories-test-vid",
        "video.thumbnail-path=${java.io.tmpdir}/memories-test-vid/th",
        "app.broadcast-posts=true"
})
class FeatureFlowTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PostRepository postRepository;

    @Test
    void socialFeaturesWorkEndToEnd() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String alice = register("alice" + suffix);
        String bob = register("bob" + suffix);
        String aliceId = me(alice).get("id").asText();
        String bobId = me(bob).get("id").asText();

        // Alice posts a photo, a story, and a story from two days ago.
        String postId = createPost(alice, "image/a.jpg", "IMAGE");
        String storyId = createPost(alice, "image/s.jpg", "STORY_IMAGE");
        String oldStoryId = createPost(alice, "image/old.jpg", "STORY_IMAGE");
        Post oldStory = postRepository.findById(UUID.fromString(oldStoryId)).orElseThrow();
        oldStory.setTimestamp(ZonedDateTime.now().minusDays(2));
        postRepository.save(oldStory);

        // Bob likes and saves the photo.
        call(bob, get("/api/posts/like/" + postId));
        call(bob, post("/api/posts/" + postId + "/save"));

        // Legacy feed: posts plus active stories, with Bob's like/save flags. Expired story is gone.
        JsonNode legacyFeed = read(bob, get("/api/posts/feed"));
        assertThat(ids(legacyFeed)).contains(postId, storyId).doesNotContain(oldStoryId);
        JsonNode photo = find(legacyFeed, postId);
        assertThat(photo.get("isLiked").asBoolean()).isTrue();
        assertThat(photo.get("isSaved").asBoolean()).isTrue();
        assertThat(photo.get("likes").asInt()).isEqualTo(1);
        assertThat(find(read(alice, get("/api/posts/feed")), postId).get("isLiked").asBoolean()).isFalse();

        // Paged feed has no stories; stories come from their own endpoint.
        assertThat(ids(read(bob, get("/api/posts/feed?page=0&size=10")))).containsExactly(postId);
        assertThat(read(bob, get("/api/posts/feed?page=1&size=10"))).isEmpty();
        assertThat(ids(read(bob, get("/api/posts/stories")))).containsExactly(storyId);
        assertThat(ids(read(bob, get("/api/posts/saved")))).containsExactly(postId);
        assertThat(ids(read(bob, get("/api/posts/user/" + aliceId)))).contains(postId, storyId).doesNotContain(oldStoryId);
        assertThat(read(bob, get("/api/posts/feed/" + postId)).get("isSaved").asBoolean()).isTrue();

        // Post count on the profile ignores stories.
        assertThat(me(alice).get("posts").asInt()).isEqualTo(1);

        // Unsave.
        call(bob, delete("/api/posts/" + postId + "/save"));
        assertThat(read(bob, get("/api/posts/saved"))).isEmpty();

        // Only the owner can edit a caption.
        mvc.perform(auth(bob, put("/api/posts/" + postId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caption\":\"hacked\"}")).andExpect(status().isForbidden());
        JsonNode edited = read(alice, put("/api/posts/" + postId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"caption\":\"Beach day\"}"));
        assertThat(edited.get("caption").asText()).isEqualTo("Beach day");

        // Comments return the saved comment and notify the post owner.
        JsonNode comment = read(bob, post("/api/posts/comments/" + postId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Lovely!\"}"));
        assertThat(comment.get("id").asText()).isNotBlank();
        assertThat(comment.get("timestamp").asText()).doesNotContain("[");
        String commentId = comment.get("id").asText();

        // Comment likes: counted once per person, flagged for the viewer, and undoable.
        call(alice, post("/api/posts/comments/" + commentId + "/like"));
        call(alice, post("/api/posts/comments/" + commentId + "/like"));
        JsonNode aliceComment = read(alice, get("/api/posts/comments/" + postId)).get(0);
        assertThat(aliceComment.get("likes").asInt()).isEqualTo(1);
        assertThat(aliceComment.get("isLiked").asBoolean()).isTrue();
        assertThat(read(bob, get("/api/posts/comments/" + postId)).get(0).get("isLiked").asBoolean()).isFalse();
        call(alice, delete("/api/posts/comments/" + commentId + "/like"));
        assertThat(read(bob, get("/api/posts/comments/" + postId)).get(0).get("likes").asInt()).isZero();
        call(alice, post("/api/posts/comments/" + commentId + "/like"));
        List<String> bobNotificationTypes = new ArrayList<>();
        read(bob, get("/api/notification/since/0")).forEach(n -> bobNotificationTypes.add(n.get("content").asText()));
        assertThat(bobNotificationTypes).contains("NEW_COMMENT_LIKE");

        // Highlights: the owner's archive keeps expired stories, and pinned ones show on the profile.
        assertThat(ids(read(alice, get("/api/posts/stories/archive")))).containsExactly(storyId, oldStoryId);
        mvc.perform(auth(bob, post("/api/posts/" + oldStoryId + "/highlight"))).andExpect(status().isForbidden());
        mvc.perform(auth(alice, post("/api/posts/" + postId + "/highlight"))).andExpect(status().isBadRequest());
        call(alice, post("/api/posts/" + oldStoryId + "/highlight"));
        JsonNode highlights = read(bob, get("/api/posts/user/" + aliceId + "/highlights"));
        assertThat(ids(highlights)).containsExactly(oldStoryId);
        assertThat(highlights.get(0).get("isHighlighted").asBoolean()).isTrue();
        assertThat(ids(read(bob, get("/api/posts/stories")))).doesNotContain(oldStoryId);
        call(alice, delete("/api/posts/" + oldStoryId + "/highlight"));
        assertThat(read(bob, get("/api/posts/user/" + aliceId + "/highlights"))).isEmpty();
        call(alice, post("/api/posts/" + oldStoryId + "/highlight"));

        // Story viewers: only counted for others, only visible to the owner.
        call(bob, post("/api/posts/" + storyId + "/view"));
        call(bob, post("/api/posts/" + storyId + "/view"));
        call(alice, post("/api/posts/" + storyId + "/view"));
        JsonNode viewers = read(alice, get("/api/posts/" + storyId + "/views"));
        assertThat(ids(viewers)).containsExactly(bobId);
        mvc.perform(auth(bob, get("/api/posts/" + storyId + "/views"))).andExpect(status().isForbidden());

        // Follow creates a notification.
        call(bob, get("/api/users/follow/" + aliceId));

        List<String> aliceNotificationTypes = new ArrayList<>();
        read(alice, get("/api/notification/since/0")).forEach(n -> aliceNotificationTypes.add(n.get("content").asText()));
        assertThat(aliceNotificationTypes).contains("NEW_LIKE", "NEW_COMMENT", "NEW_FOLLOWER");
        // Alice doesn't get notified about her own new posts.
        assertThat(aliceNotificationTypes).doesNotContain("NEW_POST", "NEW_STORY");

        // Messages: unread count, then read after opening the thread.
        call(bob, post("/api/message").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiver\":\"" + aliceId + "\",\"content\":\"Hi mom\"}"));
        JsonNode inbox = read(alice, get("/api/message/conversations"));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("user").get("id").asText()).isEqualTo(bobId);
        assertThat(inbox.get(0).get("lastMessage").asText()).isEqualTo("Hi mom");
        assertThat(inbox.get(0).get("unreadCount").asInt()).isEqualTo(1);
        assertThat(read(alice, get("/api/message/with/" + bobId))).hasSize(1);
        assertThat(read(alice, get("/api/message/conversations")).get(0).get("unreadCount").asInt()).isZero();
        assertThat(read(bob, get("/api/message/conversations")).get(0).get("unreadCount").asInt()).isZero();
        // Nobody can message themselves.
        mvc.perform(auth(alice, post("/api/message").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiver\":\"" + aliceId + "\",\"content\":\"Note to self\"}")))
                .andExpect(status().isBadRequest());

        // The receiver can delete messages one by one or several at once. That hides them only
        // for the receiver; the sender's delete removes the message for both.
        call(bob, post("/api/message").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiver\":\"" + aliceId + "\",\"content\":\"Second\"}"));
        call(bob, post("/api/message").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiver\":\"" + aliceId + "\",\"content\":\"Third\"}"));
        call(alice, post("/api/message").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiver\":\"" + bobId + "\",\"content\":\"Reply\"}"));
        JsonNode thread = read(alice, get("/api/message/with/" + bobId));
        assertThat(thread).hasSize(4);
        String firstId = thread.get(0).get("id").asText();
        String secondId = thread.get(1).get("id").asText();
        String thirdId = thread.get(2).get("id").asText();
        String replyId = thread.get(3).get("id").asText();
        call(alice, delete("/api/message/" + firstId));
        call(alice, post("/api/message/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + secondId + "\",\"" + thirdId + "\"]}"));
        assertThat(ids(read(alice, get("/api/message/with/" + bobId)))).containsExactly(replyId);
        assertThat(ids(read(bob, get("/api/message/with/" + aliceId)))).containsExactly(firstId, secondId, thirdId, replyId);
        assertThat(read(alice, get("/api/message/conversations")).get(0).get("lastMessage").asText()).isEqualTo("Reply");
        // Bob can't delete Alice's copy of her own message; Alice's delete removes it for both.
        call(bob, delete("/api/message/" + replyId));
        assertThat(ids(read(alice, get("/api/message/with/" + bobId)))).containsExactly(replyId);
        call(alice, delete("/api/message/" + replyId));
        assertThat(ids(read(bob, get("/api/message/with/" + aliceId)))).containsExactly(firstId, secondId, thirdId);

        // Blocking hides the person's posts; unblocking brings them back.
        call(bob, get("/api/users/block/" + aliceId));
        assertThat(read(bob, get("/api/posts/feed"))).isEmpty();
        assertThat(read(bob, get("/api/posts/stories"))).isEmpty();
        assertThat(ids(read(bob, get("/api/users/blocked")))).containsExactly(aliceId);
        call(bob, delete("/api/users/block/" + aliceId));
        assertThat(ids(read(bob, get("/api/posts/feed")))).contains(postId);

        // Login failures say whether the account exists.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody" + suffix + "@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob" + suffix + "@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob" + suffix + "@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Token refresh.
        JsonNode refreshed = read(bob, post("/api/auth/refresh"));
        assertThat(refreshed.get("token").asText()).isNotBlank();

        // Deleting an account removes everything that pointed at it.
        call(alice, delete("/api/users/me"));
        assertThat(read(bob, get("/api/posts/feed"))).isEmpty();
        assertThat(read(bob, get("/api/message/conversations"))).isEmpty();
        assertThat(read(bob, get("/api/notification/since/0"))).isEmpty();
    }

    private String register(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret123\"}";
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private JsonNode me(String token) throws Exception {
        return read(token, get("/api/users/me"));
    }

    private String createPost(String token, String url, String type) throws Exception {
        String body = "{\"caption\":\"c\",\"imageUrl\":\"" + url + "\",\"postType\":\"" + type + "\"}";
        return read(token, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body)).get("id").asText();
    }

    private MockHttpServletRequestBuilder auth(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private void call(String token, MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(auth(token, request)).andExpect(status().is2xxSuccessful());
    }

    private JsonNode read(String token, MockHttpServletRequestBuilder request) throws Exception {
        String response = mvc.perform(auth(token, request)).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private static JsonNode find(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (node.get("id").asText().equals(id)) {
                return node;
            }
        }
        throw new AssertionError("Post " + id + " not found");
    }
}
