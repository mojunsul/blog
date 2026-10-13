package io.github.mojunsul.blog.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
public abstract class IntegrationTestSupport {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		TestContainers.registerDatasource(registry);
	}
}
