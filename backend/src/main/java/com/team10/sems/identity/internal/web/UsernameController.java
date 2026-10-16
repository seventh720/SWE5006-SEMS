package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.internal.application.UsernameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/username")
public class UsernameController {
    private final UsernameService usernames;
    public UsernameController(UsernameService usernames) { this.usernames = usernames; }

    @PutMapping
    public UsernameView update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UsernameRequest request) {
        return new UsernameView(usernames.update(UUID.fromString(jwt.getSubject()), request.username()));
    }

    public record UsernameRequest(@NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "must contain only letters, numbers, dot, underscore or hyphen") String username) { }
    public record UsernameView(String username) { }
}
