package io.github.mojunsul.blog.domain.category.dto;

public record CategoryResponse(Long id, String name, String slug, long postCount) {
}
