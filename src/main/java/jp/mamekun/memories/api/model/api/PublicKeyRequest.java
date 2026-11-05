package jp.mamekun.memories.api.model.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeyRequest {
    @NotBlank
    private String publicKey;
}
