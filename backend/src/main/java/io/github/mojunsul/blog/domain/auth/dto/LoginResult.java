package io.github.mojunsul.blog.domain.auth.dto;

public record LoginResult(String username, String accessToken, String refreshToken) {
}
