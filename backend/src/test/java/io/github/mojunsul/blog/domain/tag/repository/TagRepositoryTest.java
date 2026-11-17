package io.github.mojunsul.blog.domain.tag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.mojunsul.blog.domain.tag.entity.Tag;
import io.github.mojunsul.blog.fixture.TagFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;

class TagRepositoryTest extends RepositoryTestSupport {

	@Autowired
	TagRepository tagRepository;

	@Test
	void 주어진_이름의_태그만_조회한다() {
		// given
		tagRepository.saveAll(List.of(TagFixture.tag("spring"), TagFixture.tag("jpa"), TagFixture.tag("jwt")));

		// when
		List<Tag> found = tagRepository.findAllByNameIn(List.of("spring", "jwt", "missing"));

		// then
		assertThat(found).extracting(Tag::getName).containsExactlyInAnyOrder("spring", "jwt");
	}
}
