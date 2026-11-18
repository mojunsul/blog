package io.github.mojunsul.blog.domain.post.repository;

import static io.github.mojunsul.blog.domain.category.entity.QCategory.category;
import static io.github.mojunsul.blog.domain.post.entity.QPost.post;
import static io.github.mojunsul.blog.domain.tag.entity.QPostTag.postTag;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PostQueryRepository {

	private final JPAQueryFactory queryFactory;

	public Page<Post> searchPublished(PostSearchRequest request) {
		Pageable pageable = request.toPageable();
		List<Post> posts = queryFactory.selectFrom(post)
			.leftJoin(post.category, category)
			.fetchJoin()
			.where(post.status.eq(PostStatus.PUBLISHED), categorySlugEq(request.category()),
				hasTag(request.tag()), titleOrContentContains(request.q()))
			.orderBy(post.publishedAt.desc(), post.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();

		// fetch join은 count에 필요 없고 JPQL count 쿼리에서 오류가 되므로 따로 만든다.
		JPAQuery<Long> countQuery = queryFactory.select(post.count())
			.from(post)
			.leftJoin(post.category, category)
			.where(post.status.eq(PostStatus.PUBLISHED), categorySlugEq(request.category()),
				hasTag(request.tag()), titleOrContentContains(request.q()));
		return PageableExecutionUtils.getPage(posts, pageable, countQuery::fetchOne);
	}

	private BooleanExpression categorySlugEq(String slug) {
		return slug == null ? null : category.slug.eq(slug);
	}

	// join하지 않아 글 행이 늘지 않으므로 distinct 없이 count가 정확하다.
	private BooleanExpression hasTag(String tagName) {
		if (tagName == null) {
			return null;
		}
		return JPAExpressions.selectOne()
			.from(postTag)
			.where(postTag.post.eq(post), postTag.tag.name.eq(tagName))
			.exists();
	}

	private BooleanExpression titleOrContentContains(String keyword) {
		if (keyword == null) {
			return null;
		}
		return post.title.containsIgnoreCase(keyword).or(post.content.containsIgnoreCase(keyword));
	}
}
