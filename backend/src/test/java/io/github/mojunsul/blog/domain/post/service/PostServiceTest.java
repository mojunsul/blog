package io.github.mojunsul.blog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.dto.PostCreateResponse;
import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.PostRequest;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-22T01:00:00Z");

	@Mock
	PostRepository postRepository;

	@Mock
	CategoryRepository categoryRepository;

	Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	PostService postService;

	@BeforeEach
	void setUp() {
		postService = new PostService(postRepository, categoryRepository, clock);
	}

	private static PostRequest request(PostStatus status, Long categoryId) {
		return new PostRequest("제목", "slug", null, "본문", null, categoryId, status);
	}

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("violation",
			new ConstraintViolationException("violation", new SQLException(), constraint));
	}

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

	@Test
	void 작성_PUBLISHED면_현재_시각이_발행_시각이다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willAnswer(i -> i.getArgument(0));
		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);

		// when
		PostCreateResponse response = postService.create(request(PostStatus.PUBLISHED, null));

		// then
		then(postRepository).should().saveAndFlush(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(saved.getValue().getPublishedAt()).isEqualTo(NOW);
		assertThat(response.slug()).isEqualTo("slug");
	}

	@Test
	void 작성_DRAFT면_발행_시각이_없다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willAnswer(i -> i.getArgument(0));
		ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);

		// when
		postService.create(request(PostStatus.DRAFT, null));

		// then
		then(postRepository).should().saveAndFlush(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(PostStatus.DRAFT);
		assertThat(saved.getValue().getPublishedAt()).isNull();
	}

	@Test
	void 작성에_없는_카테고리를_보내면_CATEGORY_NOT_FOUND다() {
		// given
		given(categoryRepository.findById(9L)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> postService.create(request(PostStatus.DRAFT, 9L)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
		then(postRepository).should(never()).saveAndFlush(any());
	}

	@Test
	void 작성_flush에서_slug_유니크_위반이_나면_DUPLICATE_SLUG다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willThrow(violation("post_slug_key"));

		// when & then
		assertThatThrownBy(() -> postService.create(request(PostStatus.DRAFT, null)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.DUPLICATE_SLUG);
	}

	@Test
	void 작성_flush에서_다른_제약_위반이_나면_그대로_던진다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willThrow(violation("post_category_id_fkey"));

		// when & then
		assertThatThrownBy(() -> postService.create(request(PostStatus.DRAFT, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 작성_flush에서_제약_이름이_없는_위반이_나도_그대로_던진다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willThrow(new DataIntegrityViolationException("x"));

		// when & then
		assertThatThrownBy(() -> postService.create(request(PostStatus.DRAFT, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 수정하면_본문과_카테고리를_바꿔_응답한다() {
		// given
		Category category = CategoryFixture.category("backend");
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));
		given(categoryRepository.findById(2L)).willReturn(Optional.of(category));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(PostStatus.DRAFT, 2L));

		// then
		assertThat(response.title()).isEqualTo("제목");
		assertThat(response.slug()).isEqualTo("slug");
		assertThat(response.category().slug()).isEqualTo("backend");
		then(postRepository).should().flush();
	}

	@Test
	void 수정에_카테고리를_보내지_않으면_카테고리가_지워진다() {
		// given
		given(postRepository.findWithCategoryById(1L))
			.willReturn(Optional.of(PostFixture.draft("old", CategoryFixture.category("backend"))));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(PostStatus.DRAFT, null));

		// then
		assertThat(response.category()).isNull();
		assertThat(response.categoryId()).isNull();
	}

	@Test
	void 수정할_글이_없으면_POST_NOT_FOUND다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> postService.update(1L, request(PostStatus.DRAFT, 9L)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.POST_NOT_FOUND);
		then(categoryRepository).should(never()).findById(any());
	}

	@Test
	void 수정에_없는_카테고리를_보내면_CATEGORY_NOT_FOUND다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));
		given(categoryRepository.findById(9L)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> postService.update(1L, request(PostStatus.DRAFT, 9L)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
	}

	@Test
	void 한_번도_발행하지_않은_글을_PUBLISHED로_수정하면_현재_시각이_발행_시각이다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(PostStatus.PUBLISHED, null));

		// then
		assertThat(response.status()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(response.publishedAt()).isEqualTo(NOW);
	}

	@Test
	void 발행된_글을_PUBLISHED로_수정해도_발행_시각은_유지된다() {
		// given
		Instant first = Instant.parse("2026-10-01T01:00:00Z");
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.published("old", first)));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(PostStatus.PUBLISHED, null));

		// then
		assertThat(response.publishedAt()).isEqualTo(first);
	}

	@Test
	void 발행된_글을_DRAFT로_수정하면_상태만_바뀌고_발행_시각은_유지된다() {
		// given
		Instant first = Instant.parse("2026-10-01T01:00:00Z");
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.published("old", first)));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(PostStatus.DRAFT, null));

		// then
		assertThat(response.status()).isEqualTo(PostStatus.DRAFT);
		assertThat(response.publishedAt()).isEqualTo(first);
	}

	@Test
	void 발행했다_DRAFT로_돌린_글을_다시_발행해도_최초_발행_시각이_유지된다() {
		// given
		Instant first = Instant.parse("2026-10-01T01:00:00Z");
		Post post = PostFixture.published("old", first);
		post.toDraft();
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(post));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(PostStatus.PUBLISHED, null));

		// then
		assertThat(response.publishedAt()).isEqualTo(first);
	}

	@Test
	void 수정_flush에서_slug_유니크_위반이_나면_DUPLICATE_SLUG다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));
		willThrow(violation("post_slug_key")).given(postRepository).flush();

		// when & then
		assertThatThrownBy(() -> postService.update(1L, request(PostStatus.DRAFT, null)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.DUPLICATE_SLUG);
	}

	@Test
	void 수정_flush에서_다른_제약_위반이_나면_그대로_던진다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));
		willThrow(violation("post_category_id_fkey")).given(postRepository).flush();

		// when & then
		assertThatThrownBy(() -> postService.update(1L, request(PostStatus.DRAFT, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 수정_flush에서_낙관적_락_예외가_나면_POST_NOT_FOUND다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));
		willThrow(new ObjectOptimisticLockingFailureException(Post.class, 1L)).given(postRepository).flush();

		// when & then
		assertThatThrownBy(() -> postService.update(1L, request(PostStatus.DRAFT, null)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.POST_NOT_FOUND);
	}

	@Test
	void 삭제된_행이_없으면_POST_NOT_FOUND다() {
		// given
		given(postRepository.deleteByIdReturningCount(1L)).willReturn(0);

		// when & then
		assertThatThrownBy(() -> postService.delete(1L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.POST_NOT_FOUND);
	}

	@Test
	void 삭제된_행이_있으면_예외_없이_끝난다() {
		// given
		given(postRepository.deleteByIdReturningCount(1L)).willReturn(1);

		// when & then
		assertThatCode(() -> postService.delete(1L)).doesNotThrowAnyException();
	}
}
