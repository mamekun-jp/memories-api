package jp.mamekun.memories.api.config;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(
        name = "app.push-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ApnsConfig {

    @Value("${apns.key-id}")
    private String keyId;

    @Value("${apns.team-id}")
    private String teamId;

    @Value("${apns.production}")
    private boolean production;

    @Value("${apns.private-key-base64}")
    private String privateKeyBase64;

    @Bean
    public ApnsClient apnsClient() throws IOException, NoSuchAlgorithmException, InvalidKeyException {

        byte[] decoded = Base64.getDecoder().decode(privateKeyBase64);

        ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(
                new ByteArrayInputStream(decoded),
                teamId,
                keyId
        );

        return new ApnsClientBuilder()
                .setApnsServer(production
                        ? ApnsClientBuilder.PRODUCTION_APNS_HOST
                        : ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setSigningKey(signingKey)
                .build();
    }
}
