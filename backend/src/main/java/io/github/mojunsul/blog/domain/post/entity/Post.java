package io.github.mojunsul.blog.domain.post.entity;

import java.time.Instant;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, unique = true, length = 200)
	private String slug;

	@Column(length = 500)
	private String summary;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(length = 500)
	private String thumbnailUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PostStatus status;

	@Column(nullable = false)
	private long viewCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	private Instant publishedAt;

	@Builder
	private Post(String title, String slug, String summary, String content, String thumbnailUrl, Category category) {
		this.title = title;
		this.slug = slug;
		this.summary = summary;
		this.content = content;
		this.thumbnailUrl = thumbnailUrl;
		this.category = category;
		this.status = PostStatus.DRAFT;
	}

	// 발행 시각은 최초 발행 때만 정한다. 다시 발행해도 글 목록 순서가 바뀌지 않게 하기 위해서다.
	public void publish(Instant now) {
		this.status = PostStatus.PUBLISHED;
		if (this.publishedAt == null) {
			this.publishedAt = now;
		}
	}

	// 발행 시각은 지우지 않는다. 다시 발행할 때 최초 발행 시각이 유지돼야 하기 때문이다.
	public void toDraft() {
		this.status = PostStatus.DRAFT;
	}

	public void update(String title, String slug, String summary, String content, String thumbnailUrl,
		Category category) {
		this.title = title;
		this.slug = slug;
		this.summary = summary;
		this.content = content;
		this.thumbnailUrl = thumbnailUrl;
		this.category = category;
	}
}
