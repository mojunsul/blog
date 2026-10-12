package io.github.mojunsul.blog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JpaAuditingConfigTest {

	@Test
	void 감사_시간은_주입된_Clock_기준이다() {
		Instant fixed = Instant.parse("2026-10-12T01:00:00Z");
		Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

		var provider = new JpaAuditingConfig().auditingDateTimeProvider(clock);

		assertThat(provider.getNow()).contains(fixed);
	}
}
