package io.github.mojunsul.blog.domain.post.dto;

import java.time.Instant;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPostSummaryResponse(
	Long id,
	String title,
	String slug,
	PostStatus status,
	@Schema(nullable = true) PostCategoryResponse category,
	@Schema(nullable = true) Instant publishedAt,
	Instant createdAt,
	Instant updatedAt) {

	public static AdminPostSummaryResponse from(Post post) {
		return new AdminPostSummaryResponse(post.getId(), post.getTitle(), post.getSlug(), post.getStatus(),
			PostCategoryResponse.from(post.getCategory()), post.getPublishedAt(), post.getCreatedAt(),
			post.getUpdatedAt());
	}
}
