package io.github.mojunsul.blog.domain.post.dto;

import java.time.Instant;
import java.util.List;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

public record PostDetailResponse(
	Long id,
	String title,
	String slug,
	@Schema(nullable = true) String summary,
	String content,
	@Schema(nullable = true) String thumbnailUrl,
	@Schema(nullable = true) PostCategoryResponse category,
	List<String> tags,
	Instant publishedAt,
	Instant updatedAt,
	long viewCount) {

	public static PostDetailResponse from(Post post, List<String> tags) {
		return new PostDetailResponse(post.getId(), post.getTitle(), post.getSlug(), post.getSummary(),
			post.getContent(), post.getThumbnailUrl(), PostCategoryResponse.from(post.getCategory()), tags,
			post.getPublishedAt(), post.getUpdatedAt(), post.getViewCount());
	}
}
