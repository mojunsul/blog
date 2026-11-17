package io.github.mojunsul.blog.domain.tag.entity;

import io.github.mojunsul.blog.domain.post.entity.Post;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTag {

	@EmbeddedId
	private PostTagId id;

	@MapsId("postId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Post post;

	@MapsId("tagId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Tag tag;

	// 저장된(id가 있는) Post·Tag만 받는다. 키를 여기서 채운다.
	@Builder
	private PostTag(Post post, Tag tag) {
		this.id = new PostTagId(post.getId(), tag.getId());
		this.post = post;
		this.tag = tag;
	}
}
