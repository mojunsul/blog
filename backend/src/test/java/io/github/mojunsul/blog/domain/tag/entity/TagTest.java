package io.github.mojunsul.blog.domain.tag.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.mojunsul.blog.fixture.TagFixture;

class TagTest {

	@Test
	void 정규화하면_앞뒤_공백이_지워지고_소문자가_된다() {
		// when & then
		assertThat(Tag.normalize("  Spring BOOT  ")).isEqualTo("spring boot");
	}

	@Test
	void 태그를_만들면_정규화된_이름이_저장된다() {
		// when
		Tag tag = TagFixture.tag("  JPA ");

		// then
		assertThat(tag.getName()).isEqualTo("jpa");
	}
}
