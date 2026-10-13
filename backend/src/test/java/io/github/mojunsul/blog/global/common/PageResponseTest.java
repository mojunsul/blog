package io.github.mojunsul.blog.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	void 중간_페이지를_변환한다() {
		// given
		var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

		// when
		var response = PageResponse.from(page);

		// then
		assertThat(response.content()).containsExactly("a", "b");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.totalPages()).isEqualTo(3);
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	void 마지막_페이지는_hasNext가_false다() {
		// given
		var page = new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5);

		// when
		var response = PageResponse.from(page);

		// then
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void 빈_결과도_변환한다() {
		// given
		var page = new PageImpl<String>(List.of(), PageRequest.of(0, 10), 0);

		// when
		var response = PageResponse.from(page);

		// then
		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
		assertThat(response.hasNext()).isFalse();
	}
}
