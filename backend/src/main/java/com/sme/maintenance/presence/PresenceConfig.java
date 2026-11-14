package com.sme.maintenance.presence;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PresenceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
