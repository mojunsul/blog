package io.github.mojunsul.blog.domain.post.dto;

import java.time.Instant;
import java.util.List;

import io.github.mojunsul.blog.domain.post.entity.Post;

public record PostSummaryResponse(
	Long id,
	String title,
	String slug,
	String summary,
	String thumbnailUrl,
	PostCategoryResponse category,
	List<String> tags,
	Instant publishedAt) {

	// 태그는 M4에서 채운다. 그 전까지는 응답 형식을 맞추기 위해 빈 목록을 내려준다.
	public static PostSummaryResponse from(Post post) {
		return new PostSummaryResponse(post.getId(), post.getTitle(), post.getSlug(), post.getSummary(),
			post.getThumbnailUrl(), PostCategoryResponse.from(post.getCategory()), List.of(), post.getPublishedAt());
	}
}
