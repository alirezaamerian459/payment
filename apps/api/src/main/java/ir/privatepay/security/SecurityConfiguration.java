package ir.privatepay.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    @Profile("!sandbox")
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Profile("!sandbox")
    UserDetailsService noAccountsConfigured() {
        return username -> {
            throw new UsernameNotFoundException("No accounts are configured in this foundation.");
        };
    }

    @Bean
    @Profile("sandbox")
    UserDetailsService sandboxHasNoPasswordAccounts() {
        return username -> {
            throw new UsernameNotFoundException("Sandbox endpoints use local demo-role headers only.");
        };
    }

    @Bean
    @Profile("sandbox")
    SecurityFilterChain sandboxSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
