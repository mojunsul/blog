package io.github.mojunsul.blog.domain.post.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;

public interface PostRepository extends JpaRepository<Post, Long> {

	Page<Post> findAllByStatus(PostStatus status, Pageable pageable);

	Optional<Post> findBySlugAndStatus(String slug, PostStatus status);
}
