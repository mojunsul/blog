package io.github.mojunsul.blog.domain.post.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.service.PostService;
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.security.CookieUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;
	private final CookieUtil cookieUtil;

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

	@PostMapping("/{slug}/views")
	public ResponseEntity<Void> increaseViewCount(
		@PathVariable
		String slug,
		@CookieValue(name = CookieUtil.POST_VIEWED, required = false)
		String viewed) {
		if (viewed != null) {
			return ResponseEntity.noContent().build();
		}
		postService.increaseViewCount(slug);
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookieUtil.postViewed(slug).toString())
			.build();
	}
}
