package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Body for renaming a group ({@code name}), adding members ({@code userIds}) or (un)making an admin ({@code admin}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupUpdateRequest {
    private String name;
    private List<String> userIds;
    private Boolean admin;
}
