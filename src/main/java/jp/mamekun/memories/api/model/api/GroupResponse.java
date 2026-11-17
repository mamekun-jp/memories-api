package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** A group with its members and what the requester may do in it. */
@Data
@AllArgsConstructor
public class GroupResponse {
    private String id;
    private String name;
    private String createdBy;
    private String createdAt;
    private List<Member> members;
    private Boolean isAdmin;
    private Boolean isCreator;

    @Data
    @AllArgsConstructor
    public static class Member {
        private UserResponse user;
        private Boolean admin;
        private Boolean creator;
    }
}
