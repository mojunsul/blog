package io.github.mojunsul.blog.domain.tag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.domain.tag.dto.TagResponse;
import io.github.mojunsul.blog.domain.tag.entity.Tag;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.fixture.PostTagFixture;
import io.github.mojunsul.blog.fixture.TagFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;

class TagRepositoryTest extends RepositoryTestSupport {

	@Autowired
	TagRepository tagRepository;

	@Autowired
	PostRepository postRepository;

	@Autowired
	PostTagRepository postTagRepository;

	@Test
	void 주어진_이름의_태그만_조회한다() {
		// given
		tagRepository.saveAll(List.of(TagFixture.tag("spring"), TagFixture.tag("jpa"), TagFixture.tag("jwt")));

		// when
		List<Tag> found = tagRepository.findAllByNameIn(List.of("spring", "jwt", "missing"));

		// then
		assertThat(found).extracting(Tag::getName).containsExactlyInAnyOrder("spring", "jwt");
	}

	@Test
	void 발행_글_수를_세고_DRAFT와_연결_없는_태그는_제외한다() {
		// given
		Tag spring = tagRepository.save(TagFixture.tag("spring"));
		Tag draftOnly = tagRepository.save(TagFixture.tag("draft-only"));
		tagRepository.save(TagFixture.tag("unused"));
		Post published = postRepository.save(PostFixture.published("p1", (Category)null));
		Post draft = postRepository.save(PostFixture.draft("d1"));
		postTagRepository.saveAll(List.of(PostTagFixture.postTag(published, spring),
			PostTagFixture.postTag(draft, spring), PostTagFixture.postTag(draft, draftOnly)));

		// when
		List<TagResponse> result = tagRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).containsExactly(new TagResponse("spring", 1));
	}

	@Test
	void 글_수_내림차순_같으면_이름_오름차순으로_정렬한다() {
		// given
		Tag b = tagRepository.save(TagFixture.tag("b"));
		Tag a = tagRepository.save(TagFixture.tag("a"));
		Tag c = tagRepository.save(TagFixture.tag("c"));
		Post p1 = postRepository.save(PostFixture.published("p1", (Category)null));
		Post p2 = postRepository.save(PostFixture.published("p2", (Category)null));
		postTagRepository.saveAll(List.of(PostTagFixture.postTag(p1, b), PostTagFixture.postTag(p1, a),
			PostTagFixture.postTag(p1, c), PostTagFixture.postTag(p2, c)));

		// when
		List<TagResponse> result = tagRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).containsExactly(new TagResponse("c", 2), new TagResponse("a", 1), new TagResponse("b", 1));
	}
}
