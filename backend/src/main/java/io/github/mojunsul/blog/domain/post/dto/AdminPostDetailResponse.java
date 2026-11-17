package io.github.mojunsul.blog.domain.post.dto;

import java.time.Instant;
import java.util.List;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPostDetailResponse(
	Long id,
	String title,
	String slug,
	@Schema(nullable = true) String summary,
	String content,
	@Schema(nullable = true) String thumbnailUrl,
	PostStatus status,
	@Schema(nullable = true) Long categoryId,
	@Schema(nullable = true) PostCategoryResponse category,
	List<String> tags,
	@Schema(nullable = true) Instant publishedAt,
	Instant createdAt,
	Instant updatedAt) {

	public static AdminPostDetailResponse from(Post post, List<String> tags) {
		return new AdminPostDetailResponse(post.getId(), post.getTitle(), post.getSlug(), post.getSummary(),
			post.getContent(), post.getThumbnailUrl(), post.getStatus(),
			post.getCategory() == null ? null : post.getCategory().getId(),
			PostCategoryResponse.from(post.getCategory()), tags, post.getPublishedAt(), post.getCreatedAt(),
			post.getUpdatedAt());
	}
}
