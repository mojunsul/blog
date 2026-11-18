package io.github.mojunsul.blog.domain.tag.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.mojunsul.blog.domain.tag.dto.TagResponse;
import io.github.mojunsul.blog.domain.tag.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

	List<Tag> findAllByNameIn(Collection<String> names);

	// inner join이라 PUBLISHED 글이 없는 태그는 결과에서 빠진다.
	@Query("""
		select new io.github.mojunsul.blog.domain.tag.dto.TagResponse(t.name, count(pt))
		from PostTag pt
		join pt.tag t
		join pt.post p
		where p.status = io.github.mojunsul.blog.domain.post.entity.PostStatus.PUBLISHED
		group by t.id, t.name
		order by count(pt) desc, t.name asc
		""")
	List<TagResponse> findAllWithPublishedPostCount();
}
