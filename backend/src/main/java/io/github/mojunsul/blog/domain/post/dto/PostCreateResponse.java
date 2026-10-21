package io.github.mojunsul.blog.domain.post.dto;

import io.github.mojunsul.blog.domain.post.entity.Post;

public record PostCreateResponse(Long id, String slug) {

	public static PostCreateResponse from(Post post) {
		return new PostCreateResponse(post.getId(), post.getSlug());
	}
}
