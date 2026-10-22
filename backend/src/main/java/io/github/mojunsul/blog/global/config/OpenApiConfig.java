package io.github.mojunsul.blog.global.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.media.Schema;

// 프론트 타입 생성(openapi-typescript)이 응답 필드를 선택(?)으로 만들지 않고 null을 잃지 않게 명세를 보정한다.
@Configuration
public class OpenApiConfig {

	@Bean
	OpenApiCustomizer responseSchemaCustomizer() {
		return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
			if (name.contains("Response") && schema.getProperties() != null) {
				Map<String, Schema> properties = schema.getProperties();
				schema.setRequired(new ArrayList<>(properties.keySet()));
				properties.replaceAll((property, value) -> isNullableRef(value) ? nullableRef(value) : value);
			}
		});
	}

	private boolean isNullableRef(Schema<?> schema) {
		return schema.get$ref() != null && schema.getTypes() != null && schema.getTypes().contains("null");
	}

	// $ref와 type: null이 한 스키마에 있으면 생성기가 null을 버리므로 oneOf로 나눈다
	private Schema<?> nullableRef(Schema<?> schema) {
		Schema<?> ref = new Schema<>().$ref(schema.get$ref());
		Schema<?> nullType = new Schema<>().types(Set.of("null"));
		return new Schema<>().oneOf(List.of(ref, nullType));
	}
}
