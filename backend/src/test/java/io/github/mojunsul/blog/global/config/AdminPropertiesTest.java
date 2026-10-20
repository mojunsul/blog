package io.github.mojunsul.blog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminPropertiesTest {

	@Test
	void toString에_비밀번호_원문이_나오지_않는다() {
		// given
		AdminProperties properties = new AdminProperties("admin", "super-secret-pw", true);

		// when
		String result = properties.toString();

		// then
		assertThat(result).doesNotContain("super-secret-pw").contains("admin", "****");
	}

	@Test
	void 비밀번호가_비어_있으면_toString에_마스킹_표시를_넣지_않는다() {
		// given
		AdminProperties properties = new AdminProperties("admin", "", false);

		// when
		String result = properties.toString();

		// then
		assertThat(result).doesNotContain("****");
	}
}
