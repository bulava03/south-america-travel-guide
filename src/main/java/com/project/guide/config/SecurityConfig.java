package com.project.guide.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Security configuration.
 * Public pages are open to everyone, the admin panel and admin API require the ADMIN role.
 * The admin account is configured via spring.security.user.* properties.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .formLogin(withDefaults())
                .httpBasic(withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/"));

        return http.build();
    }
}
