package io.github.mojunsul.blog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.dto.PostCreateResponse;
import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.PostRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.repository.PostQueryRepository;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.domain.tag.entity.PostTag;
import io.github.mojunsul.blog.domain.tag.entity.Tag;
import io.github.mojunsul.blog.domain.tag.repository.PostTagRepository;
import io.github.mojunsul.blog.domain.tag.repository.TagRepository;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.fixture.PostTagFixture;
import io.github.mojunsul.blog.fixture.TagFixture;
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-22T01:00:00Z");

	@Mock
	PostRepository postRepository;

	@Mock
	PostQueryRepository postQueryRepository;

	@Mock
	CategoryRepository categoryRepository;

	@Mock
	TagRepository tagRepository;

	@Mock
	PostTagRepository postTagRepository;

	Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	PostService postService;

	@BeforeEach
	void setUp() {
		postService = new PostService(postRepository, postQueryRepository, categoryRepository, tagRepository,
			postTagRepository, clock);
	}

	private static PostRequest request(PostStatus status, Long categoryId) {
		return new PostRequest("제목", "slug", null, "본문", null, categoryId, status, null);
	}

	private static PostRequest request(List<String> tags) {
		return new PostRequest("제목", "slug", null, "본문", null, null, PostStatus.DRAFT, tags);
	}

	private static PostTag postTag(Post post, String name) {
		return PostTagFixture.postTag(post, TagFixture.tag(name));
	}

	private static DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("violation",
			new ConstraintViolationException("violation", new SQLException(), constraint));
	}

	@Test
	void 발행된_글이_있으면_상세_응답으로_변환한다() {
		// given
		Post post = PostFixture.published("my-post", Instant.parse("2026-10-11T01:00:00Z"));
		ReflectionTestUtils.setField(post, "id", 1L);
		given(postRepository.findBySlugAndStatus("my-post", PostStatus.PUBLISHED)).willReturn(Optional.of(post));
		given(postTagRepository.findAllWithTagByPostIdIn(List.of(1L)))
			.willReturn(List.of(postTag(post, "jpa"), postTag(post, "spring")));

		// when
		PostDetailResponse response = postService.getPublishedPost("my-post");

		// then
		assertThat(response.slug()).isEqualTo("my-post");
		assertThat(response.content()).isEqualTo(post.getContent());
		assertThat(response.category()).isNull();
		assertThat(response.tags()).containsExactly("jpa", "spring");
		assertThat(response.publishedAt()).isEqualTo(Instant.parse("2026-10-11T01:00:00Z"));
	}

	@Test
	void 공개_목록의_tags는_글별로_묶이고_태그가_없는_글은_빈_목록이다() {
		// given
		Post first = PostFixture.published("first", Instant.parse("2026-10-11T01:00:00Z"));
		Post second = PostFixture.published("second", Instant.parse("2026-10-10T01:00:00Z"));
		ReflectionTestUtils.setField(first, "id", 1L);
		ReflectionTestUtils.setField(second, "id", 2L);
		PostSearchRequest request = new PostSearchRequest(null, null, null, null, null);
		given(postQueryRepository.searchPublished(request))
			.willReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 10), 2));
		given(postTagRepository.findAllWithTagByPostIdIn(List.of(1L, 2L)))
			.willReturn(List.of(postTag(first, "jpa"), postTag(first, "spring")));

		// when
		PageResponse<PostSummaryResponse> response = postService.getPublishedPosts(request);

		// then
		assertThat(response.content()).extracting(PostSummaryResponse::tags)
			.containsExactly(List.of("jpa", "spring"), List.of());
	}

	@Test
	void 공개_목록이_비어_있으면_태그를_조회하지_않는다() {
		// given
		PostSearchRequest request = new PostSearchRequest(null, null, "none", null, null);
		given(postQueryRepository.searchPublished(request))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		// when
		PageResponse<PostSummaryResponse> response = postService.getPublishedPosts(request);

		// then
		assertThat(response.content()).isEmpty();
		then(postTagRepository).shouldHaveNoInteractions();
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
	void 관리자_단건의_tags는_findAllWithTagByPostIdIn_결과의_태그_이름이다() {
		// given
		Post post = PostFixture.draft("draft");
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(post));
		given(postTagRepository.findAllWithTagByPostIdIn(List.of(1L)))
			.willReturn(List.of(postTag(post, "jpa"), postTag(post, "spring")));

		// when
		AdminPostDetailResponse response = postService.getAdminPost(1L);

		// then
		assertThat(response.tags()).containsExactly("jpa", "spring");
	}

	@Test
	void 작성에_tags가_있으면_있는_태그는_두고_없는_이름만_만들어_연결한다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willAnswer(i -> i.getArgument(0));
		given(tagRepository.findAllByNameIn(List.of("spring", "jwt"))).willReturn(List.of(TagFixture.tag("spring")));

		// when
		postService.create(request(List.of("spring", "jwt")));

		// then
		ArgumentCaptor<List<Tag>> created = ArgumentCaptor.captor();
		then(tagRepository).should().saveAll(created.capture());
		assertThat(created.getValue()).extracting(Tag::getName).containsExactly("jwt");
		ArgumentCaptor<List<PostTag>> linked = ArgumentCaptor.captor();
		then(postTagRepository).should().saveAll(linked.capture());
		assertThat(linked.getValue()).extracting(pt -> pt.getTag().getName()).containsExactlyInAnyOrder("spring",
			"jwt");
		then(postTagRepository).should(never()).deleteAllByPostId(any());
	}

	@Test
	void 작성에_tags가_비면_태그를_조회하거나_저장하지_않는다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willAnswer(i -> i.getArgument(0));

		// when
		postService.create(request(List.of()));

		// then
		then(tagRepository).shouldHaveNoInteractions();
		then(postTagRepository).shouldHaveNoInteractions();
	}

	@Test
	void 작성_flush에서_slug_위반이_나면_태그를_연결하지_않는다() {
		// given
		given(postRepository.saveAndFlush(any(Post.class))).willThrow(violation("post_slug_key"));

		// when & then
		assertThatThrownBy(() -> postService.create(request(List.of("spring")))).isInstanceOf(BusinessException.class);
		then(tagRepository).shouldHaveNoInteractions();
	}

	@Test
	void 수정은_연결을_모두_지운_뒤_새_목록으로_다시_연결하고_응답_tags는_다시_읽는다() {
		// given
		Post post = PostFixture.draft("old");
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(post));
		given(tagRepository.findAllByNameIn(List.of("spring"))).willReturn(List.of(TagFixture.tag("spring")));
		given(postTagRepository.findAllWithTagByPostIdIn(List.of(1L)))
			.willReturn(List.of(postTag(post, "spring")));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(List.of("spring")));

		// then
		InOrder inOrder = inOrder(postRepository, postTagRepository);
		inOrder.verify(postRepository).flush();
		inOrder.verify(postTagRepository).deleteAllByPostId(1L);
		inOrder.verify(postTagRepository).saveAll(any());
		inOrder.verify(postTagRepository).findAllWithTagByPostIdIn(List.of(1L));
		assertThat(response.tags()).containsExactly("spring");
	}

	@Test
	void 수정에_tags가_비면_연결만_지우고_태그는_조회하지_않는다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));

		// when
		AdminPostDetailResponse response = postService.update(1L, request(List.of()));

		// then
		then(postTagRepository).should().deleteAllByPostId(1L);
		then(postTagRepository).should(never()).saveAll(any());
		then(tagRepository).shouldHaveNoInteractions();
		assertThat(response.tags()).isEmpty();
	}

	@Test
	void 수정_flush가_실패하면_연결을_지우지_않는다() {
		// given
		given(postRepository.findWithCategoryById(1L)).willReturn(Optional.of(PostFixture.draft("old")));
		willThrow(violation("post_slug_key")).given(postRepository).flush();

		// when & then
		assertThatThrownBy(() -> postService.update(1L, request(List.of("spring"))))
			.isInstanceOf(BusinessException.class);
		then(postTagRepository).should(never()).deleteAllByPostId(any());
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
