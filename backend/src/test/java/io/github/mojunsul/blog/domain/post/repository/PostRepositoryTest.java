package io.github.mojunsul.blog.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.support.IntegrationTestSupport;

@Transactional
class PostRepositoryTest extends IntegrationTestSupport {

	@Autowired
	PostRepository postRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Test
	void 상태로_조회하면_PUBLISHED_글만_나온다() {
		Category category = categoryRepository.save(Category.builder().name("Backend").slug("backend").build());
		Post published = post("published", category);
		published.publish(Instant.parse("2026-10-13T01:00:00Z"));
		postRepository.save(published);
		postRepository.save(post("draft", category));

		var page = postRepository.findAllByStatus(PostStatus.PUBLISHED, PageRequest.of(0, 10));

		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("published");
	}

	@Test
	void slug와_PUBLISHED_상태로_조회하면_DRAFT_글은_나오지_않는다() {
		postRepository.save(post("draft", null));

		assertThat(postRepository.findBySlugAndStatus("draft", PostStatus.PUBLISHED)).isEmpty();
	}

	@Test
	void slug가_중복되면_저장할_수_없다() {
		postRepository.saveAndFlush(post("same-slug", null));

		assertThatThrownBy(() -> postRepository.saveAndFlush(post("same-slug", null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Post post(String slug, Category category) {
		return Post.builder().title("제목").slug(slug).content("본문").category(category).build();
	}
}
