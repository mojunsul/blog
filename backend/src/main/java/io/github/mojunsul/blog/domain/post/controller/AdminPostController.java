package io.github.mojunsul.blog.domain.post.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.service.PostService;
import io.github.mojunsul.blog.global.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

	private final PostService postService;

	@GetMapping
	public PageResponse<AdminPostSummaryResponse> getPosts(
		@Valid
		@ModelAttribute
		AdminPostSearchRequest request) {
		return postService.getAdminPosts(request);
	}

	@GetMapping("/{id}")
	public AdminPostDetailResponse getPost(
		@PathVariable
		Long id) {
		return postService.getAdminPost(id);
	}
}
