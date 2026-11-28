package io.github.mojunsul.blog.domain.post.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

	private static final Logger log = LoggerFactory.getLogger(PostService.class);

	// post.slug 인라인 UNIQUE의 PostgreSQL 기본 제약 이름. FK 등 다른 위반을 409로 오인하지 않으려고 이름으로 가린다.
	private static final String SLUG_CONSTRAINT = "post_slug_key";

	private final PostRepository postRepository;
	private final PostQueryRepository postQueryRepository;
	private final CategoryRepository categoryRepository;
	private final TagRepository tagRepository;
	private final PostTagRepository postTagRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<PostSummaryResponse> getPublishedPosts(PostSearchRequest request) {
		Page<Post> posts = postQueryRepository.searchPublished(request);
		Map<Long, List<String>> tagNames = findTagNamesByPostId(posts.map(Post::getId).getContent());
		return PageResponse
			.from(posts.map(post -> PostSummaryResponse.from(post, tagNames.getOrDefault(post.getId(), List.of()))));
	}

	@Transactional(readOnly = true)
	public PostDetailResponse getPublishedPost(String slug) {
		return postRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
			.map(post -> PostDetailResponse.from(post, findTagNames(post.getId())))
			.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
	}

	@Transactional
	public void increaseViewCount(String slug) {
		if (postRepository.increaseViewCount(slug, PostStatus.PUBLISHED) == 0) {
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminPostSummaryResponse> getAdminPosts(AdminPostSearchRequest request) {
		Pageable pageable = request.toPageable();
		Page<Post> posts = request.status() == null
			? postRepository.findAllByOrderByIdDesc(pageable)
			: postRepository.findAllByStatusOrderByIdDesc(request.status(), pageable);
		return PageResponse.from(posts.map(AdminPostSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public AdminPostDetailResponse getAdminPost(Long id) {
		return postRepository.findWithCategoryById(id)
			.map(post -> AdminPostDetailResponse.from(post, findTagNames(id)))
			.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
	}

	// 제약 위반이 커밋 시점에 서비스 밖에서 나오면 500이 되므로 saveAndFlush·flush로 여기서 잡는다
	@Transactional
	public PostCreateResponse create(PostRequest request) {
		Category category = findCategory(request.categoryId());
		Post post = Post.builder()
			.title(request.title())
			.slug(request.slug())
			.summary(request.summary())
			.content(request.content())
			.thumbnailUrl(request.thumbnailUrl())
			.category(category)
			.build();
		applyStatus(post, request.status());
		try {
			postRepository.saveAndFlush(post);
		} catch (DataIntegrityViolationException e) {
			throw translate(e);
		}
		linkTags(post, request.tags());
		log.info("Post created: id={}, slug={}", post.getId(), post.getSlug());
		return PostCreateResponse.from(post);
	}

	@Transactional
	public AdminPostDetailResponse update(Long id, PostRequest request) {
		Post post = postRepository.findWithCategoryById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		Category category = findCategory(request.categoryId());
		post.update(request.title(), request.slug(), request.summary(), request.content(), request.thumbnailUrl(),
			category);
		applyStatus(post, request.status());
		try {
			postRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw translate(e);
		} catch (ObjectOptimisticLockingFailureException e) {
			// 조회 뒤 flush 전에 다른 요청이 지운 경우
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
		// 벌크 삭제라 영속성 컨텍스트의 PostTag와 무관하다. flush 뒤에 해야 slug 위반으로 실패한 수정이 연결을 지우지 않는다.
		postTagRepository.deleteAllByPostId(id);
		linkTags(post, request.tags());
		log.info("Post updated: id={}, slug={}", id, post.getSlug());
		return AdminPostDetailResponse.from(post, findTagNames(id));
	}

	@Transactional
	public void delete(Long id) {
		if (postRepository.deleteByIdReturningCount(id) == 0) {
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}
		log.info("Post deleted: id={}", id);
	}

	private void linkTags(Post post, List<String> names) {
		if (names.isEmpty()) {
			return;
		}
		List<Tag> tags = tagRepository.findAllByNameIn(names);
		Set<String> existing = tags.stream().map(Tag::getName).collect(Collectors.toSet());
		List<Tag> created = names.stream()
			.filter(name -> !existing.contains(name))
			.map(name -> Tag.builder().name(name).build())
			.toList();
		tagRepository.saveAll(created);
		postTagRepository.saveAll(Stream.concat(tags.stream(), created.stream())
			.map(tag -> PostTag.builder().post(post).tag(tag).build())
			.toList());
	}

	private Map<Long, List<String>> findTagNamesByPostId(List<Long> postIds) {
		if (postIds.isEmpty()) {
			return Map.of();
		}
		return postTagRepository.findAllWithTagByPostIdIn(postIds)
			.stream()
			.collect(Collectors.groupingBy(postTag -> postTag.getPost().getId(),
				Collectors.mapping(postTag -> postTag.getTag().getName(), Collectors.toList())));
	}

	private List<String> findTagNames(Long postId) {
		return postTagRepository.findAllWithTagByPostIdIn(List.of(postId))
			.stream()
			.map(postTag -> postTag.getTag().getName())
			.toList();
	}

	private Category findCategory(Long categoryId) {
		if (categoryId == null) {
			return null;
		}
		return categoryRepository.findById(categoryId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
	}

	private void applyStatus(Post post, PostStatus status) {
		if (status == PostStatus.PUBLISHED) {
			post.publish(Instant.now(clock));
		} else {
			post.toDraft();
		}
	}

	private RuntimeException translate(DataIntegrityViolationException e) {
		if (e.getCause() instanceof ConstraintViolationException cause
			&& SLUG_CONSTRAINT.equals(cause.getConstraintName())) {
			return new BusinessException(ErrorCode.DUPLICATE_SLUG);
		}
		return e;
	}
}
