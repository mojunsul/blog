package io.github.mojunsul.blog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

class OpenApiConfigTest {

	@Test
	void Response_스키마는_모든_property가_required가_된다() {
		// given
		OpenAPI openApi = openApiWith("PostSummaryResponse", responseSchema());

		// when
		new OpenApiConfig().responseSchemaCustomizer().customise(openApi);

		// then
		assertThat(schema(openApi, "PostSummaryResponse").getRequired()).containsExactlyInAnyOrder("id", "summary",
			"category");
	}

	@Test
	void ref와_null을_함께_가진_property는_oneOf로_바뀐다() {
		// given
		OpenAPI openApi = openApiWith("PostSummaryResponse", responseSchema());

		// when
		new OpenApiConfig().responseSchemaCustomizer().customise(openApi);

		// then
		Schema<?> category = (Schema<?>)schema(openApi, "PostSummaryResponse").getProperties().get("category");
		assertThat(category.get$ref()).isNull();
		assertThat(category.getTypes()).isNull();
		assertThat(category.getOneOf()).hasSize(2);
		assertThat(category.getOneOf().get(0).get$ref()).isEqualTo("#/components/schemas/PostCategoryResponse");
		assertThat(category.getOneOf().get(1).getTypes()).containsExactly("null");
	}

	@Test
	void 스칼라_nullable은_그대로_둔다() {
		// given
		OpenAPI openApi = openApiWith("PostSummaryResponse", responseSchema());

		// when
		new OpenApiConfig().responseSchemaCustomizer().customise(openApi);

		// then
		Schema<?> summary = (Schema<?>)schema(openApi, "PostSummaryResponse").getProperties().get("summary");
		assertThat(summary.getTypes()).containsExactlyInAnyOrder("string", "null");
		assertThat(summary.getOneOf()).isNull();
	}

	@Test
	void Request_스키마는_바뀌지_않는다() {
		// given
		OpenAPI openApi = openApiWith("PostCreateRequest", responseSchema());

		// when
		new OpenApiConfig().responseSchemaCustomizer().customise(openApi);

		// then
		Schema<?> request = schema(openApi, "PostCreateRequest");
		assertThat(request.getRequired()).isNull();
		assertThat(((Schema<?>)request.getProperties().get("category")).get$ref()).isNotNull();
	}

	private Schema<?> responseSchema() {
		Schema<?> id = new Schema<>().types(Set.of("integer"));
		Schema<?> summary = new Schema<>().types(Set.of("string", "null"));
		Schema<?> category = new Schema<>().$ref("#/components/schemas/PostCategoryResponse").types(Set.of("null"));
		ObjectSchema schema = new ObjectSchema();
		schema.addProperty("id", id);
		schema.addProperty("summary", summary);
		schema.addProperty("category", category);
		return schema;
	}

	private OpenAPI openApiWith(String name, Schema<?> schema) {
		return new OpenAPI().components(new Components().addSchemas(name, schema));
	}

	private Schema<?> schema(OpenAPI openApi, String name) {
		return openApi.getComponents().getSchemas().get(name);
	}
}
