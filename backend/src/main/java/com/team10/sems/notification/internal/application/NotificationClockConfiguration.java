package com.team10.sems.notification.internal.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationClockConfiguration {
    @Bean
    Clock notificationClock() { return Clock.systemUTC(); }
}
