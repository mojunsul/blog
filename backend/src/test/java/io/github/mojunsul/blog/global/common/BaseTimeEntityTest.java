package io.github.mojunsul.blog.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;

@Transactional
class BaseTimeEntityTest extends IntegrationTestSupport {

	@MockitoBean
	Clock clock;

	@Autowired
	PostRepository postRepository;

	@Autowired
	EntityManager em;

	@Test
	void 저장하면_생성_시각과_수정_시각이_채워진다() {
		Instant savedAt = Instant.parse("2026-10-13T01:00:00Z");
		given(clock.instant()).willReturn(savedAt);

		Post post = postRepository.saveAndFlush(Post.builder().title("제목").slug("slug").content("본문").build());

		assertThat(post.getCreatedAt()).isEqualTo(savedAt);
		assertThat(post.getUpdatedAt()).isEqualTo(savedAt);
	}

	@Test
	void 수정하면_수정_시각만_바뀐다() {
		Instant savedAt = Instant.parse("2026-10-13T01:00:00Z");
		Instant updatedAt = Instant.parse("2026-10-13T02:00:00Z");
		given(clock.instant()).willReturn(savedAt, updatedAt);
		Post post = postRepository.saveAndFlush(Post.builder().title("제목").slug("slug").content("본문").build());

		post.publish(updatedAt);
		em.flush();
		em.clear();

		Post found = postRepository.findById(post.getId()).orElseThrow();
		assertThat(found.getCreatedAt()).isEqualTo(savedAt);
		assertThat(found.getUpdatedAt()).isEqualTo(updatedAt);
	}
}
