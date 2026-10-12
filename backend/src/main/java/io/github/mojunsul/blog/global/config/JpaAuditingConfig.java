package io.github.mojunsul.blog.global.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	// createdAt/updatedAt도 Clock 빈 기준으로 채워서, 테스트에서 Clock을 고정하면 이 값들도 고정되게 한다.
	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock));
	}
}
