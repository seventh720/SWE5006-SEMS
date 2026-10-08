package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.internal.application.AvatarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/avatar")
public class AvatarController {
    private final AvatarService avatars;
    public AvatarController(AvatarService avatars) { this.avatars = avatars; }
    @GetMapping
    public AvatarService.AvatarView read(@AuthenticationPrincipal Jwt jwt) {
        return avatars.read(UUID.fromString(jwt.getSubject()));
    }
    @PutMapping
    public AvatarService.AvatarView save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvatarInput input) {
        return avatars.save(UUID.fromString(jwt.getSubject()), input.dataUrl());
    }
    public record AvatarInput(@Size(max = 350000) String dataUrl) {
        @Override public String toString() { return "AvatarInput[image omitted]"; }
    }
}
