package com.team10.sems.identity.internal.application;

import com.team10.sems.identity.BookingProfile;
import com.team10.sems.identity.internal.domain.UserAccount;
import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingProfileService {
    private final UserAccountRepository users;

    public BookingProfileService(UserAccountRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public BookingProfile read(UUID user) { return account(user).bookingProfile(); }

    public BookingProfile save(UUID user, BookingProfile profile) {
        UserAccount account = account(user);
        account.updateBookingProfile(profile);
        users.flush();
        return account.bookingProfile();
    }

    private UserAccount account(UUID user) {
        return users.findById(user).orElseThrow(() -> new UserNotFoundException(user));
    }
}
