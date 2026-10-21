package io.github.mojunsul.blog.domain.post.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;

public interface PostRepository extends JpaRepository<Post, Long> {

	@EntityGraph(attributePaths = "category")
	Page<Post> findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Optional<Post> findBySlugAndStatus(String slug, PostStatus status);

	@EntityGraph(attributePaths = "category")
	Page<Post> findAllByOrderByIdDesc(Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Page<Post> findAllByStatusOrderByIdDesc(PostStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Optional<Post> findWithCategoryById(Long id);

	// post_tag는 DB FK(ON DELETE CASCADE)가 지운다. 지운 행 수로 존재 여부를 판정한다.
	@Modifying
	@Query("delete from Post p where p.id = :id")
	int deleteByIdReturningCount(
		@Param("id")
		Long id);
}
