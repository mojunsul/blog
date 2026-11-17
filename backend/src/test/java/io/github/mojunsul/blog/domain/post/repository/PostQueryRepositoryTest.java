package io.github.mojunsul.blog.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.tag.entity.Tag;
import io.github.mojunsul.blog.domain.tag.repository.PostTagRepository;
import io.github.mojunsul.blog.domain.tag.repository.TagRepository;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.fixture.PostTagFixture;
import io.github.mojunsul.blog.fixture.TagFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;

@Import(PostQueryRepository.class)
class PostQueryRepositoryTest extends RepositoryTestSupport {

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-13T01:00:00Z");

	@Autowired
	PostQueryRepository postQueryRepository;

	@Autowired
	EntityManager em;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	TagRepository tagRepository;

	@Autowired
	PostTagRepository postTagRepository;

	@Autowired
	PostRepository postRepository;

	private static PostSearchRequest search(String category, String tag, String q) {
		return new PostSearchRequest(null, null, category, tag, q);
	}

	private Post savePublished(String slug, String title, String content, Category category) {
		Post post = Post.builder().title(title).slug(slug).content(content).category(category).build();
		post.publish(PUBLISHED_AT);
		return postRepository.save(post);
	}

	private void link(Post post, String... tagNames) {
		for (String name : tagNames) {
			Tag tag = tagRepository.findAllByNameIn(List.of(name))
				.stream()
				.findFirst()
				.orElseGet(() -> tagRepository.save(TagFixture.tag(name)));
			postTagRepository.save(PostTagFixture.postTag(post, tag));
		}
	}

	@Test
	void 필터가_없으면_PUBLISHED_글만_나온다() {
		// given
		postRepository.save(PostFixture.published("published", PUBLISHED_AT));
		postRepository.save(PostFixture.draft("draft"));

		// when
		Page<Post> page = postQueryRepository.searchPublished(search(null, null, null));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("published");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void DRAFT는_어떤_필터로도_나오지_않는다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));
		Post draft = postRepository.save(
			Post.builder().title("spring").slug("draft").content("spring").category(category).build());
		link(draft, "spring");

		// when
		Page<Post> page = postQueryRepository.searchPublished(search("backend", "spring", "spring"));

		// then
		assertThat(page.getContent()).isEmpty();
	}

	@Test
	void category는_slug가_정확히_같은_글만_나온다() {
		// given
		Category backend = categoryRepository.save(CategoryFixture.category("backend"));
		Category frontend = categoryRepository.save(CategoryFixture.category("frontend"));
		savePublished("in-backend", "t", "c", backend);
		savePublished("in-frontend", "t", "c", frontend);
		savePublished("no-category", "t", "c", null);

		// when
		Page<Post> page = postQueryRepository.searchPublished(search("backend", null, null));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("in-backend");
	}

	@Test
	void category의_대소문자가_다르면_나오지_않는다() {
		// given
		Category backend = categoryRepository.save(CategoryFixture.category("backend"));
		savePublished("in-backend", "t", "c", backend);

		// when
		Page<Post> page = postQueryRepository.searchPublished(search("Backend", null, null));

		// then
		assertThat(page.getContent()).isEmpty();
	}

	@Test
	void category로_조회하면_카테고리가_함께_로딩된다() {
		// given
		Category backend = categoryRepository.save(CategoryFixture.category("backend"));
		savePublished("in-backend", "t", "c", backend);
		em.flush();
		em.clear();

		// when
		Post found = postQueryRepository.searchPublished(search(null, null, null)).getContent().get(0);

		// then
		assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(found.getCategory())).isTrue();
	}

	@Test
	void tag가_연결된_글만_나오고_태그가_여러_개인_글도_한_번만_나온다() {
		// given
		Post both = savePublished("both", "t", "c", null);
		Post onlyJpa = savePublished("only-jpa", "t", "c", null);
		savePublished("none", "t", "c", null);
		link(both, "spring", "jpa");
		link(onlyJpa, "jpa");

		// when
		Page<Post> page = postQueryRepository.searchPublished(search(null, "jpa", null));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("only-jpa", "both");
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void q는_제목과_본문에서_각각_대소문자를_무시하고_찾는다() {
		// given
		savePublished("in-title", "Spring 정리", "내용", null);
		savePublished("in-content", "제목", "본문에 SPRING이 있다", null);
		savePublished("neither", "제목", "내용", null);

		// when
		Page<Post> page = postQueryRepository.searchPublished(search(null, null, "spring"));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("in-content", "in-title");
	}

	@Test
	void q의_퍼센트와_밑줄은_일반_문자로_찾는다() {
		// given
		savePublished("percent", "100% 달성", "c", null);
		savePublished("underscore", "snake_case", "c", null);
		savePublished("plain", "100 달성 snakeXcase", "c", null);

		// when
		Page<Post> percent = postQueryRepository.searchPublished(search(null, null, "%"));
		Page<Post> underscore = postQueryRepository.searchPublished(search(null, null, "_"));

		// then
		assertThat(percent.getContent()).extracting(Post::getSlug).containsExactly("percent");
		assertThat(underscore.getContent()).extracting(Post::getSlug).containsExactly("underscore");
	}

	@Test
	void 세_필터는_AND로_조합된다() {
		// given
		Category backend = categoryRepository.save(CategoryFixture.category("backend"));
		Post match = savePublished("match", "Spring", "c", backend);
		Post wrongTag = savePublished("wrong-tag", "Spring", "c", backend);
		Post wrongCategory = savePublished("wrong-category", "Spring", "c", null);
		link(match, "jpa");
		link(wrongTag, "jwt");
		link(wrongCategory, "jpa");

		// when
		Page<Post> page = postQueryRepository.searchPublished(search("backend", "jpa", "spring"));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("match");
	}

	@Test
	void 없는_카테고리와_태그는_빈_페이지다() {
		// given
		savePublished("post", "t", "c", null);

		// when & then
		assertThat(postQueryRepository.searchPublished(search("none", null, null)).getContent()).isEmpty();
		assertThat(postQueryRepository.searchPublished(search(null, "none", null)).getContent()).isEmpty();
	}

	@Test
	void 발행_시각_내림차순으로_조회하고_같으면_id_내림차순이다() {
		// given
		postRepository.save(PostFixture.published("same-first", PUBLISHED_AT));
		postRepository.save(PostFixture.published("newest", PUBLISHED_AT.plusSeconds(60)));
		postRepository.save(PostFixture.published("same-second", PUBLISHED_AT));

		// when
		Page<Post> page = postQueryRepository.searchPublished(search(null, null, null));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug)
			.containsExactly("newest", "same-second", "same-first");
	}

	@Test
	void 두_번째_페이지와_전체_건수를_돌려준다() {
		// given
		for (int i = 0; i < 3; i++) {
			postRepository.save(PostFixture.published("post-" + i, PUBLISHED_AT.plusSeconds(i)));
		}

		// when
		Page<Post> page = postQueryRepository.searchPublished(new PostSearchRequest(1, 2, null, null, null));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("post-0");
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.hasNext()).isFalse();
	}
}
