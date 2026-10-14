package io.github.mojunsul.blog.domain.post.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;

public interface PostRepository extends JpaRepository<Post, Long> {

	@EntityGraph(attributePaths = "category")
	Page<Post> findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Optional<Post> findBySlugAndStatus(String slug, PostStatus status);
}
