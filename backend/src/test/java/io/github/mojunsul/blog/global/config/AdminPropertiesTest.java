package io.github.mojunsul.blog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminPropertiesTest {

	@Test
	void toString에_비밀번호_원문이_나오지_않는다() {
		// given
		AdminProperties properties = new AdminProperties("admin", "super-secret-pw", true);

		// when
		String result = properties.toString();

		// then
		assertThat(result).doesNotContain("super-secret-pw")
			.contains("username=admin", "password=****", "required=true");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void 비밀번호가_비어_있으면_마스킹_표시_없이_빈_값으로_표시한다(String password) {
		// given
		AdminProperties properties = new AdminProperties("admin", password, false);

		// when
		String result = properties.toString();

		// then
		assertThat(result).doesNotContain("****")
			.contains("username=admin", "password=,", "required=false");
	}
}
