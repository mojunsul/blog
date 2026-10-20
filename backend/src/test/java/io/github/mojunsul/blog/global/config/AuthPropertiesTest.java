package io.github.mojunsul.blog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

	@Test
	void toString에_JWT_키_원문이_나오지_않는다() {
		// given
		AuthProperties properties = new AuthProperties("super-secret-key", true, false);

		// when
		String result = properties.toString();

		// then
		assertThat(result).doesNotContain("super-secret-key")
			.contains("jwtSecret=****", "jwtSecretRequired=true", "secureCookie=false");
	}

	@Test
	void JWT_키가_비어_있으면_마스킹_표시_없이_빈_값으로_표시한다() {
		// given
		AuthProperties properties = new AuthProperties("", false, true);

		// when
		String result = properties.toString();

		// then
		assertThat(result).doesNotContain("****").contains("jwtSecret=,");
	}
}
