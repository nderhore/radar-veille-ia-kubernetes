package fr.liveveille.radar.api.web;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Horloge injectable : les tests figent le temps, la production utilise UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
