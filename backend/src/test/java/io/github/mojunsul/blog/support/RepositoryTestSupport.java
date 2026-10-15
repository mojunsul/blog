package io.github.mojunsul.blog.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.mojunsul.blog.global.config.ClockConfig;
import io.github.mojunsul.blog.global.config.JpaAuditingConfig;

// 내장 DB로 바꾸지 않고 운영과 같은 PostgreSQL로 쿼리를 검증한다. 감사 설정은 슬라이스에 포함되지 않아 직접 가져온다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ClockConfig.class})
@ActiveProfiles("test")
public abstract class RepositoryTestSupport {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		TestContainers.registerDatasource(registry);
	}
}
