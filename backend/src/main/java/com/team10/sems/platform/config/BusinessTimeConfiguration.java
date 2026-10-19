package com.team10.sems.platform.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "businessDateTimeProvider")
public class BusinessTimeConfiguration {
    @Bean
    Clock businessClock() { return Clock.systemUTC(); }

    @Bean
    DateTimeProvider businessDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
