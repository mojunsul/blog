package io.github.mojunsul.blog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

	@Mock
	PostRepository postRepository;

	@InjectMocks
	PostService postService;

	@Test
	void 발행된_글이_있으면_상세_응답으로_변환한다() {
		// given
		var post = PostFixture.published("my-post", Instant.parse("2026-10-11T01:00:00Z"));
		given(postRepository.findBySlugAndStatus("my-post", PostStatus.PUBLISHED)).willReturn(Optional.of(post));

		// when
		PostDetailResponse response = postService.getPublishedPost("my-post");

		// then
		assertThat(response.slug()).isEqualTo("my-post");
		assertThat(response.content()).isEqualTo(post.getContent());
		assertThat(response.category()).isNull();
		assertThat(response.tags()).isEmpty();
		assertThat(response.publishedAt()).isEqualTo(Instant.parse("2026-10-11T01:00:00Z"));
	}

	@Test
	void 글이_없으면_POST_NOT_FOUND_예외가_발생한다() {
		// given
		given(postRepository.findBySlugAndStatus("missing", PostStatus.PUBLISHED)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> postService.getPublishedPost("missing"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.POST_NOT_FOUND);
	}
}
