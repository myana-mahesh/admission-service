package com.bothash.admissionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	
	@Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // disable CSRF (safe for dev; configure in prod)
            .authorizeHttpRequests(auth -> auth
            	.requestMatchers("/api/invoices/download/**").permitAll()
                .anyRequest().authenticated() // allow every request without authentication
            ).oauth2ResourceServer(oauth2 -> oauth2.jwt());
		
//		http.csrf(csrf -> csrf.disable())
//	     .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

