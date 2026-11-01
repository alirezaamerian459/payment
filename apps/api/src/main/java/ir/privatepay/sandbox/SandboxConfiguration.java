package ir.privatepay.sandbox;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SandboxConfiguration {
    @Bean
    Clock privatePayClock() { return Clock.system(ZoneId.of("Asia/Tehran")); }
}
