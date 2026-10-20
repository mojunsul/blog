package io.github.mojunsul.blog.domain.category.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mojunsul.blog.domain.category.dto.CategoryResponse;
import io.github.mojunsul.blog.domain.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	// 상태 조건을 WHERE가 아닌 ON에 둬야 PUBLISHED 글이 없는 카테고리도 0으로 남는다.
	@Query("""
		select new io.github.mojunsul.blog.domain.category.dto.CategoryResponse(c.id, c.name, c.slug, count(p))
		from Category c
		left join Post p on p.category = c and p.status = io.github.mojunsul.blog.domain.post.entity.PostStatus.PUBLISHED
		group by c.id, c.name, c.slug
		order by c.name asc, c.id asc
		""")
	List<CategoryResponse> findAllWithPublishedPostCount();

	// 글의 category_id는 DB FK(ON DELETE SET NULL)가 처리한다. 지운 행 수로 존재 여부를 판정한다.
	@Modifying
	@Query("delete from Category c where c.id = :id")
	int deleteByIdReturningCount(
		@Param("id")
		Long id);
}
