package io.github.mojunsul.blog.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {}
