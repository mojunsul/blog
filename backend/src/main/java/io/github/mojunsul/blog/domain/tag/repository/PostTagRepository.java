package io.github.mojunsul.blog.domain.tag.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mojunsul.blog.domain.tag.entity.PostTag;
import io.github.mojunsul.blog.domain.tag.entity.PostTagId;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {

	// 글 수와 무관하게 쿼리 하나로 태그까지 가져와 N+1을 막는다.
	@Query("select pt from PostTag pt join fetch pt.tag t where pt.post.id in :postIds order by t.name")
	List<PostTag> findAllWithTagByPostIdIn(
		@Param("postIds")
		Collection<Long> postIds);

	// clearAutomatically를 쓰면 수정 중 이미 읽은 Post가 분리되므로 쓰지 않는다.
	@Modifying
	@Query("delete from PostTag pt where pt.post.id = :postId")
	void deleteAllByPostId(
		@Param("postId")
		Long postId);
}
