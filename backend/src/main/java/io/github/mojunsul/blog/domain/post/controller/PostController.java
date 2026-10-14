package io.github.mojunsul.blog.domain.post.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.service.PostService;
import io.github.mojunsul.blog.global.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@GetMapping
	public PageResponse<PostSummaryResponse> getPosts(
		@Valid
		@ModelAttribute
		PostSearchRequest request) {
		return postService.getPublishedPosts(request);
	}

	@GetMapping("/{slug}")
	public PostDetailResponse getPost(
		@PathVariable
		String slug) {
		return postService.getPublishedPost(slug);
	}
}
