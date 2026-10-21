package io.github.mojunsul.blog.domain.post.dto;

import java.time.Instant;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;

public record AdminPostSummaryResponse(
	Long id,
	String title,
	String slug,
	PostStatus status,
	PostCategoryResponse category,
	Instant publishedAt,
	Instant createdAt,
	Instant updatedAt) {

	public static AdminPostSummaryResponse from(Post post) {
		return new AdminPostSummaryResponse(post.getId(), post.getTitle(), post.getSlug(), post.getStatus(),
			PostCategoryResponse.from(post.getCategory()), post.getPublishedAt(), post.getCreatedAt(),
			post.getUpdatedAt());
	}
}
