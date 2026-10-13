package io.github.mojunsul.blog.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;

class BaseTimeEntityTest extends RepositoryTestSupport {

	@MockitoBean
	Clock clock;

	@Autowired
	PostRepository postRepository;

	@Autowired
	EntityManager em;

	@Test
	void 저장하면_생성_시각과_수정_시각이_채워진다() {
		// given
		Instant savedAt = Instant.parse("2026-10-13T01:00:00Z");
		given(clock.instant()).willReturn(savedAt);

		// when
		Post post = postRepository.saveAndFlush(PostFixture.draft("slug"));

		// then
		assertThat(post.getCreatedAt()).isEqualTo(savedAt);
		assertThat(post.getUpdatedAt()).isEqualTo(savedAt);
	}

	@Test
	void 수정하면_수정_시각만_바뀐다() {
		// given
		Instant savedAt = Instant.parse("2026-10-13T01:00:00Z");
		Instant updatedAt = Instant.parse("2026-10-13T02:00:00Z");
		given(clock.instant()).willReturn(savedAt, updatedAt);
		Post post = postRepository.saveAndFlush(PostFixture.draft("slug"));

		// when
		post.publish(updatedAt);
		em.flush();
		em.clear();

		// then
		Post found = postRepository.findById(post.getId()).orElseThrow();
		assertThat(found.getCreatedAt()).isEqualTo(savedAt);
		assertThat(found.getUpdatedAt()).isEqualTo(updatedAt);
	}
}
