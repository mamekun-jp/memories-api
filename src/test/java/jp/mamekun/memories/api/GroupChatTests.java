package jp.mamekun.memories.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Group chats: roles, membership changes, read state and encrypted copies. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-chat;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=password",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.type.descriptor.sql=WARN"
})
class GroupChatTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void groupChatRolesAndMessages() throws Exception {
        String owner = register("owner");
        String ann = register("ann");
        String ben = register("ben");
        String cat = register("cat");
        String ownerId = id(owner), annId = id(ann), benId = id(ben), catId = id(cat);

        // Create with ann and ben; the creator is the owner and an admin.
        JsonNode group = read(owner, post("/api/groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" Family \",\"memberIds\":[\"" + annId + "\",\"" + benId + "\"]}"));
        String groupId = group.get("id").asText();
        assertThat(group.get("name").asText()).isEqualTo("Family");
        assertThat(group.get("members")).hasSize(3);
        assertThat(group.get("isCreator").asBoolean()).isTrue();
        expect(owner, post("/api/groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alone\",\"memberIds\":[]}"), status().isBadRequest());

        // Outsiders can't see it.
        expect(cat, get("/api/groups/" + groupId), status().isNotFound());
        assertThat(read(cat, get("/api/groups"))).isEmpty();

        // Messages, unread counts and "Seen".
        call(owner, post("/api/groups/" + groupId + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Hello all\"}"));
        assertThat(read(ann, get("/api/groups")).get(0).get("unreadCount").asInt()).isEqualTo(1);
        JsonNode annView = read(ann, get("/api/groups/" + groupId + "/messages"));
        assertThat(annView.get(0).get("event").asText()).isEqualTo("CREATED");
        assertThat(annView.get(1).get("content").asText()).isEqualTo("Hello all");
        assertThat(annView.get(1).get("username").asText()).isEqualTo("owner");
        assertThat(read(ann, get("/api/groups")).get(0).get("unreadCount").asInt()).isZero();
        assertThat(lastMessage(owner, groupId).get("isRead").asBoolean()).isFalse(); // ben hasn't read it
        read(ben, get("/api/groups/" + groupId + "/messages"));
        assertThat(lastMessage(owner, groupId).get("isRead").asBoolean()).isTrue();

        // Anyone can rename.
        call(ben, put("/api/groups/" + groupId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Saygis\"}"));
        assertThat(read(ann, get("/api/groups/" + groupId)).get("name").asText()).isEqualTo("Saygis");

        // Members can't add people or make admins; the owner can.
        expect(ann, post("/api/groups/" + groupId + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + catId + "\"]}"), status().isForbidden());
        call(owner, put("/api/groups/" + groupId + "/members/" + annId + "/admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"admin\":true}"));
        expect(ann, put("/api/groups/" + groupId + "/members/" + benId + "/admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"admin\":true}"), status().isForbidden());

        // A new admin can add someone, who only sees messages from then on.
        call(ann, post("/api/groups/" + groupId + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[\"" + catId + "\"]}"));
        JsonNode catView = read(cat, get("/api/groups/" + groupId + "/messages"));
        assertThat(catView).hasSize(1);
        assertThat(catView.get(0).get("event").asText()).isEqualTo("ADDED");
        assertThat(catView.get(0).get("targetName").asText()).isEqualTo("cat");

        // Admins can remove members but not the owner, nor (unless owner) other admins.
        expect(ann, delete("/api/groups/" + groupId + "/members/" + ownerId), status().isForbidden());
        call(ann, delete("/api/groups/" + groupId + "/members/" + benId));
        expect(ben, get("/api/groups/" + groupId), status().isNotFound());

        // Encrypted messages need a copy for every member, and each sees only their own copy.
        expect(owner, post("/api/groups/" + groupId + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content(securedBody(ownerId, annId)), status().isConflict());
        call(owner, post("/api/groups/" + groupId + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content(securedBody(ownerId, annId, catId)));
        assertThat(lastMessage(cat, groupId).get("content").asText()).isEqualTo("cipher-for-" + catId);
        assertThat(lastMessage(ann, groupId).get("content").asText()).isEqualTo("cipher-for-" + annId);
        assertThat(lastMessage(ann, groupId).get("secured").asBoolean()).isTrue();

        // Only your own messages can be deleted.
        String secureId = lastMessage(ann, groupId).get("id").asText();
        call(ann, post("/api/groups/" + groupId + "/messages/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + secureId + "\"]}"));
        assertThat(lastMessage(cat, groupId).get("id").asText()).isEqualTo(secureId);
        call(owner, post("/api/groups/" + groupId + "/messages/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + secureId + "\"]}"));
        assertThat(lastMessage(cat, groupId).get("id").asText()).isNotEqualTo(secureId);

        // When the owner leaves, the longest-standing admin takes over.
        call(owner, delete("/api/groups/" + groupId + "/members/" + ownerId));
        JsonNode afterLeave = read(ann, get("/api/groups/" + groupId));
        assertThat(afterLeave.get("isCreator").asBoolean()).isTrue();
        assertThat(afterLeave.get("members")).hasSize(2);
        assertThat(lastMessage(cat, groupId).get("event").asText()).isEqualTo("LEFT");

        // The last one out deletes the group.
        call(cat, delete("/api/groups/" + groupId + "/members/" + catId));
        call(ann, delete("/api/groups/" + groupId + "/members/" + annId));
        assertThat(read(ann, get("/api/groups"))).isEmpty();
    }

    private String securedBody(String... recipients) {
        StringBuilder copies = new StringBuilder();
        for (String r : recipients) {
            if (!copies.isEmpty()) copies.append(',');
            copies.append("{\"recipient\":\"").append(r).append("\",\"content\":\"cipher-for-").append(r).append("\"}");
        }
        return "{\"secured\":true,\"copies\":[" + copies + "]}";
    }

    private JsonNode lastMessage(String token, String groupId) throws Exception {
        JsonNode messages = read(token, get("/api/groups/" + groupId + "/messages"));
        return messages.get(messages.size() - 1);
    }

    private String register(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret123\"}";
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private String id(String token) throws Exception {
        return read(token, get("/api/users/me")).get("id").asText();
    }

    private MockHttpServletRequestBuilder auth(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private void expect(String token, MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        mvc.perform(auth(token, request)).andExpect(expected);
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
