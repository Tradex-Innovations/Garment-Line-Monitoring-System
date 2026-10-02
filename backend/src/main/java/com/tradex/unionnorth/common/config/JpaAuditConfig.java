package com.tradex.unionnorth.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditConfig {

    @Bean
    AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String preferredUsername = jwt.getClaimAsString("preferred_username");
                return Optional.ofNullable(preferredUsername).or(() -> Optional.ofNullable(jwt.getSubject()));
            }

            return Optional.ofNullable(authentication.getName());
        };
    }
}
