package io.github.mojunsul.blog.domain.post.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.global.common.PageResponse;
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
}
