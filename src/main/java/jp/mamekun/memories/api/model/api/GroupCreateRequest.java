package jp.mamekun.memories.api.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreateRequest {
    private String name;
    /** Everyone to add besides the creator. */
    private List<String> memberIds;
}
