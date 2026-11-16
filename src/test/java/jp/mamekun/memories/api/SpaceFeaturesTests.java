package jp.mamekun.memories.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Invites and space admins, read receipts, and media ownership, on an invite-only space. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:space-features;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=password",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.type.descriptor.sql=WARN",
        "image.path=${java.io.tmpdir}/memories-space-img",
        "image.thumbnail-path=${java.io.tmpdir}/memories-space-img/th",
        "app.space-name=Saygi Family",
        "app.admin-emails=Admin@Example.com",
        "app.invite-only=true"
})
class SpaceFeaturesTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void invitesReadReceiptsAndMediaOwnership() throws Exception {
        // Invite-only: admins can sign up without an invite, others can't.
        String admin = register("admin", "admin@example.com", null, status().isOk());
        register("stranger", "stranger@example.com", null, status().isForbidden());
        assertThat(read(admin, get("/api/users/me/settings")).get("isAdmin").asBoolean()).isTrue();

        // The admin invites someone; the invite works once.
        JsonNode invite = read(admin, post("/api/invites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresInHours\":24}"));
        String token = invite.get("token").asText();
        assertThat(invite.get("status").asText()).isEqualTo("ACTIVE");
        JsonNode info = json.readTree(mvc.perform(get("/api/auth/invite/" + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(info.get("valid").asBoolean()).isTrue();
        assertThat(info.get("spaceName").asText()).isEqualTo("Saygi Family");
        String page = mvc.perform(get("/api/auth/join/" + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Saygi Family").contains(token);

        String mom = register("mom", "mom@example.com", token, status().isOk());
        register("mom2", "mom2@example.com", token, status().isForbidden());
        assertThat(read(admin, get("/api/invites")).get(0).get("status").asText()).isEqualTo("USED");
        assertThat(read(admin, get("/api/invites")).get(0).get("usedBy").asText()).isEqualTo("mom");

        // Non-admins can't invite; revoked invites can't be used.
        mvc.perform(auth(mom, post("/api/invites"))).andExpect(status().isForbidden());
        String revoked = read(admin, post("/api/invites")).get("token").asText();
        call(admin, delete("/api/invites/" + revoked));
        register("late", "late@example.com", revoked, status().isForbidden());

        // Read receipts: shared only when both have them on.
        String adminId = read(admin, get("/api/users/me")).get("id").asText();
        String momId = read(mom, get("/api/users/me")).get("id").asText();
        call(admin, post("/api/message").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiver\":\"" + momId + "\",\"content\":\"Hi\"}"));
        read(mom, get("/api/message/with/" + adminId)); // mom reads it
        assertThat(read(admin, get("/api/message/with/" + momId)).get(0).get("isRead").asBoolean()).isTrue();
        JsonNode settings = read(mom, put("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"readReceipts\":false}"));
        assertThat(settings.get("readReceipts").asBoolean()).isFalse();
        assertThat(read(admin, get("/api/message/with/" + momId)).get(0).get("isRead").isNull()).isTrue();

        // Media: only the uploader can post a file or use it as a profile photo.
        String url = read(admin, multipart("/api/image/upload").file(png())).get("url").asText();
        String postId = createPost(admin, url, status().isOk());
        createPost(mom, url, status().isForbidden());
        mvc.perform(auth(mom, put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileImageUrl\":\"" + url + "\"}"))).andExpect(status().isForbidden());
        createPost(mom, "image/../../etc/passwd", status().isForbidden());

        // Deleting a post keeps the file while another post of the owner still uses it.
        String secondId = createPost(admin, url, status().isOk());
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "memories-space-img", url.substring("image/".length()));
        call(admin, delete("/api/posts/delete/" + postId));
        assertThat(Files.exists(file)).isTrue();
        call(admin, delete("/api/posts/delete/" + secondId));
        assertThat(Files.exists(file)).isFalse();
    }

    private String register(String username, String email, String invite, ResultMatcher expected) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"secret123\""
                + (invite == null ? "" : ",\"inviteToken\":\"" + invite + "\"") + "}";
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        if (response.isEmpty()) return null;
        JsonNode node = json.readTree(response);
        return node.has("token") ? node.get("token").asText() : null;
    }

    private String createPost(String token, String url, ResultMatcher expected) throws Exception {
        String body = "{\"caption\":\"c\",\"imageUrl\":\"" + url + "\",\"postType\":\"IMAGE\"}";
        String response = mvc.perform(auth(token, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : json.readTree(response).get("id").asText();
    }

    private static MockMultipartFile png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", UUID.randomUUID() + ".png", "image/png", out.toByteArray());
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
}
