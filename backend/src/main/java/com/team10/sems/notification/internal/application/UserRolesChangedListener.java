package com.team10.sems.notification.internal.application;

import com.team10.sems.identity.Role;
import com.team10.sems.identity.UserRolesChanged;
import com.team10.sems.notification.NotificationDeliveryService;
import com.team10.sems.notification.NotificationType;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserRolesChangedListener {
    private final NotificationDeliveryService notifications;

    public UserRolesChangedListener(NotificationDeliveryService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(UserRolesChanged event) {
        notifications.send(event.userId(), NotificationType.ROLES_CHANGED, event.operationId().toString(),
                "Your account roles have changed",
                "Previous roles: " + labels(event.before()) + ". New roles: " + labels(event.after())
                        + ". Please sign in again to use your updated permissions.", null, null, null);
    }

    private String labels(Set<Role> roles) {
        return roles.stream().sorted().map(role -> switch (role) {
            case ATTENDEE -> "Attendee";
            case ORGANIZER -> "Event Organizer";
            case STAFF -> "Event Staff";
            case ADMIN -> "System Administrator";
        }).collect(Collectors.joining(", "));
    }
}
