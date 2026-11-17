package io.github.mojunsul.blog.domain.tag.entity;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTagId implements Serializable {

	private Long postId;

	private Long tagId;

	public PostTagId(Long postId, Long tagId) {
		this.postId = postId;
		this.tagId = tagId;
	}
}
