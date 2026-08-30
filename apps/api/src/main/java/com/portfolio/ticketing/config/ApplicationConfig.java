package com.portfolio.ticketing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        AppProperties.Auth.class,
        AppProperties.Holds.class,
        AppProperties.Messaging.class
})
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
