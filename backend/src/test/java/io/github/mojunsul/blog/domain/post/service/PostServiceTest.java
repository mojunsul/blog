package io.github.mojunsul.blog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.global.common.PageResponse;
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
		Post post = PostFixture.published("my-post", Instant.parse("2026-10-11T01:00:00Z"));
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

	@Test
	void status가_없으면_상태와_관계없이_전체_글을_조회한다() {
		// given
		AdminPostSearchRequest request = new AdminPostSearchRequest(0, 10, null);
		Post draft = PostFixture.draft("draft");
		given(postRepository.findAllByOrderByIdDesc(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(draft), PageRequest.of(0, 10), 1));

		// when
		PageResponse<AdminPostSummaryResponse> response = postService.getAdminPosts(request);

		// then
		assertThat(response.content()).extracting(AdminPostSummaryResponse::slug).containsExactly("draft");
		assertThat(response.content().get(0).status()).isEqualTo(PostStatus.DRAFT);
		assertThat(response.content().get(0).publishedAt()).isNull();
		then(postRepository).should(never()).findAllByStatusOrderByIdDesc(any(), any());
	}

	@Test
	void status가_있으면_그_상태의_글만_조회한다() {
		// given
		AdminPostSearchRequest request = new AdminPostSearchRequest(0, 10, PostStatus.DRAFT);
		given(postRepository.findAllByStatusOrderByIdDesc(PostStatus.DRAFT, PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(PostFixture.draft("draft")), PageRequest.of(0, 10), 1));

		// when
		PageResponse<AdminPostSummaryResponse> response = postService.getAdminPosts(request);

		// then
		assertThat(response.content()).extracting(AdminPostSummaryResponse::slug).containsExactly("draft");
		then(postRepository).should(never()).findAllByOrderByIdDesc(any());
	}

	@Test
	void 관리자_단건은_DRAFT여도_상세_응답으로_변환한다() {
		// given
		Post post = PostFixture.draft("draft");
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(post));

		// when
		AdminPostDetailResponse response = postService.getAdminPost(1L);

		// then
		assertThat(response.slug()).isEqualTo("draft");
		assertThat(response.status()).isEqualTo(PostStatus.DRAFT);
		assertThat(response.categoryId()).isNull();
		assertThat(response.category()).isNull();
		assertThat(response.tags()).isEmpty();
	}

	@Test
	void 관리자_단건_조회에서_글이_없으면_POST_NOT_FOUND_예외가_발생한다() {
		// given
		given(postRepository.findWithCategoryById(0L)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> postService.getAdminPost(0L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.POST_NOT_FOUND);
	}
}
