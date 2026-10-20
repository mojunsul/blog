package io.github.mojunsul.blog.domain.post.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

	private final PostRepository postRepository;

	@Transactional(readOnly = true)
	public PageResponse<PostSummaryResponse> getPublishedPosts(PostSearchRequest request) {
		return PageResponse.from(
			postRepository.findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus.PUBLISHED, request.toPageable())
				.map(PostSummaryResponse::from));
	}

	@Transactional(readOnly = true)
	public PostDetailResponse getPublishedPost(String slug) {
		return postRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
			.map(PostDetailResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
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
			.map(AdminPostDetailResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
	}
}
