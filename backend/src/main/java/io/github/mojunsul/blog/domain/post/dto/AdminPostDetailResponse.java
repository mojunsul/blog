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

	// 태그는 M4에서 채운다. 그 전까지는 응답 형식을 맞추기 위해 빈 목록을 내려준다.
	public static AdminPostDetailResponse from(Post post) {
		return new AdminPostDetailResponse(post.getId(), post.getTitle(), post.getSlug(), post.getSummary(),
			post.getContent(), post.getThumbnailUrl(), post.getStatus(),
			post.getCategory() == null ? null : post.getCategory().getId(),
			PostCategoryResponse.from(post.getCategory()), List.of(), post.getPublishedAt(), post.getCreatedAt(),
			post.getUpdatedAt());
	}
}
