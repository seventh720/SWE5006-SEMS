package com.team10.sems.identity.internal.web;

import com.team10.sems.identity.BookingProfile;
import com.team10.sems.identity.internal.application.BookingProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class BookingProfileController {
    private final BookingProfileService profiles;

    public BookingProfileController(BookingProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public BookingProfile read(@AuthenticationPrincipal Jwt jwt) {
        return profiles.read(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    public BookingProfile save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingProfile profile) {
        return profiles.save(UUID.fromString(jwt.getSubject()), profile);
    }
}
