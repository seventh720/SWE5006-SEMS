package com.team10.sems.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.platform.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    @Test
    void issuedTokenContainsSubjectAndAttendeeRole() {
        String secret = "unit-test-secret-with-at-least-32-characters";
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtProperties properties = new JwtProperties(secret, "sems-test", Duration.ofMinutes(5));
        TokenService service = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                properties,
                Clock.systemUTC());
        UserAccount user = UserAccount.register("alice", "alice@example.com", "stored-hash");

        TokenService.IssuedToken issued = service.issue(user);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var jwt = decoder.decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ATTENDEE");
        assertThat(issued.expiresInSeconds()).isEqualTo(300);
    }
}
