package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResetPasswordConfirmRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;
}