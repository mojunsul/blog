package io.github.mojunsul.blog.domain.tag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.domain.tag.entity.PostTag;
import io.github.mojunsul.blog.domain.tag.entity.Tag;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.fixture.PostTagFixture;
import io.github.mojunsul.blog.fixture.TagFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;

class PostTagRepositoryTest extends RepositoryTestSupport {

	@Autowired
	PostTagRepository postTagRepository;

	@Autowired
	PostRepository postRepository;

	@Autowired
	TagRepository tagRepository;

	@Autowired
	EntityManager em;

	@Test
	void 넘긴_글의_연결만_태그_이름_오름차순으로_조회한다() {
		// given
		Post post = postRepository.save(PostFixture.draft("post"));
		Post other = postRepository.save(PostFixture.draft("other"));
		Post excluded = postRepository.save(PostFixture.draft("excluded"));
		Tag spring = tagRepository.save(TagFixture.tag("spring"));
		Tag jpa = tagRepository.save(TagFixture.tag("jpa"));
		postTagRepository.saveAll(List.of(PostTagFixture.postTag(post, spring), PostTagFixture.postTag(post, jpa),
			PostTagFixture.postTag(other, jpa), PostTagFixture.postTag(excluded, spring)));
		em.flush();
		em.clear();

		// when
		List<PostTag> found = postTagRepository.findAllWithTagByPostIdIn(List.of(post.getId(), other.getId()));

		// then
		assertThat(found).extracting(pt -> pt.getTag().getName()).containsExactly("jpa", "jpa", "spring");
		assertThat(found).extracting(pt -> pt.getId().getPostId())
			.containsExactlyInAnyOrder(post.getId(), post.getId(), other.getId());
	}

	@Test
	void 조회한_연결의_태그는_초기화되어_있다() {
		// given
		Post post = postRepository.save(PostFixture.draft("post"));
		Tag tag = tagRepository.save(TagFixture.tag("jpa"));
		postTagRepository.saveAndFlush(PostTagFixture.postTag(post, tag));
		em.clear();

		// when
		List<PostTag> found = postTagRepository.findAllWithTagByPostIdIn(List.of(post.getId()));

		// then
		assertThat(Hibernate.isInitialized(found.get(0).getTag())).isTrue();
	}

	@Test
	void 빈_목록을_넘기면_빈_결과다() {
		// when & then
		assertThat(postTagRepository.findAllWithTagByPostIdIn(List.of())).isEmpty();
	}

	@Test
	void 글의_연결만_지우고_다른_글의_연결과_태그는_남긴다() {
		// given
		Post post = postRepository.save(PostFixture.draft("post"));
		Post other = postRepository.save(PostFixture.draft("other"));
		Tag spring = tagRepository.save(TagFixture.tag("spring"));
		postTagRepository.saveAll(List.of(PostTagFixture.postTag(post, spring), PostTagFixture.postTag(other, spring)));
		em.flush();
		em.clear();

		// when
		postTagRepository.deleteAllByPostId(post.getId());

		// then
		assertThat(postTagRepository.findAllWithTagByPostIdIn(List.of(post.getId()))).isEmpty();
		assertThat(postTagRepository.findAllWithTagByPostIdIn(List.of(other.getId()))).hasSize(1);
		assertThat(tagRepository.findAll()).hasSize(1);
	}
}
