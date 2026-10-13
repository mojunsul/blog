package io.github.mojunsul.blog.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

// 모든 테스트 클래스가 컨테이너 하나를 공유한다 (JVM 종료 시 Ryuk가 정리).
final class TestContainers {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

	static {
		POSTGRES.start();
	}

	private TestContainers() {}

	static void registerDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
