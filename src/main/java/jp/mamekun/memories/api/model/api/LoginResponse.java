package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String token;
    /** Set instead of the token when login fails: USER_NOT_FOUND or WRONG_PASSWORD. */
    private String error;

    public LoginResponse(String token) {
        this.token = token;
    }

    public static LoginResponse error(String code) {
        return new LoginResponse(null, code);
    }
}
