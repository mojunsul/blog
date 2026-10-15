package io.github.mojunsul.blog.global.init;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;

// 관리자 API(M2) 전에도 M1 API와 화면을 확인하려는 로컬 전용 시드다. 글이 하나라도 있으면 다시 넣지 않는다.
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

	private final PostRepository postRepository;
	private final CategoryRepository categoryRepository;
	private final Clock clock;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (postRepository.count() > 0) {
			return;
		}

		Category backend = categoryRepository.save(Category.builder().name("Backend").slug("backend").build());
		Category frontend = categoryRepository.save(Category.builder().name("Frontend").slug("frontend").build());
		categoryRepository.save(Category.builder().name("Infra").slug("infra").build());

		postRepository.save(published(Post.builder()
			.title("Spring Boot로 REST API 시작하기")
			.slug("getting-started-with-spring-boot")
			.summary("컨트롤러, 서비스, 설정 파일까지 최소 구성으로 API 하나를 띄워 본다.")
			.content("""
				# 들어가며

				Spring Boot로 `GET /hello`를 응답하는 API를 만든다.

				## 컨트롤러

				```java
				@RestController
				class HelloController {

				    @GetMapping("/hello")
				    String hello() {
				        return "hello";
				    }
				}
				```

				## 설정

				```yaml
				server:
				  port: 8080
				spring:
				  application:
				    name: hello
				```
				""")
			.category(backend)
			.build(), 1));

		postRepository.save(published(Post.builder()
			.title("Next.js 캐시 컴포넌트 정리")
			.slug("nextjs-cache-components")
			.summary("`use cache`와 `cacheLife`로 ISR을 대체하는 방법을 정리한다.")
			.content("""
				# 캐시 컴포넌트

				데이터 함수에 `'use cache'`를 붙이고 `cacheLife`로 갱신 주기를 정한다.

				```typescript
				import { cacheLife } from 'next/cache';

				export async function getPosts() {
				  'use cache';
				  cacheLife({ revalidate: 60 });
				  const res = await fetch(`${process.env.API_INTERNAL_URL}/api/v1/posts`);
				  return res.json();
				}
				```

				## 실행

				```bash
				npm run dev
				npm run build
				```
				""")
			.category(frontend)
			.build(), 3));

		postRepository.save(published(Post.builder()
			.title("Markdown GFM 요소 모아보기")
			.slug("markdown-gfm-sample")
			.content("""
				# GFM 요소

				## 표

				| 계층 | 테스트 방식 |
				|---|---|
				| Repository | `@DataJpaTest` |
				| Controller | `@WebMvcTest` |

				## 목록

				- 첫 번째 항목
				- 두 번째 항목
				  - 중첩 항목

				1. 순서 있는 항목
				2. 다음 항목

				## 인용

				> 측정할 수 없으면 개선할 수 없다.

				## 체크리스트

				- [x] 표 렌더링
				- [ ] 코드 하이라이팅

				~~취소선~~과 **굵게**, *기울임*도 확인한다.
				""")
			.category(backend)
			.build(), 7));

		postRepository.save(published(Post.builder()
			.title("로컬 PostgreSQL을 Docker로 띄우기")
			.slug("local-postgres-with-docker")
			.summary("docker compose로 개발용 DB를 올리고 접속을 확인한다.")
			.content("""
				# 로컬 DB

				```yaml
				services:
				  postgres:
				    image: postgres:18
				    ports:
				      - "5432:5432"
				```

				```bash
				docker compose up -d
				docker compose ps
				```
				""")
			.build(), 14));

		postRepository.save(Post.builder()
			.title("작성 중인 글")
			.slug("draft-post-sample")
			.content("# 초안\n\n아직 공개하지 않은 글이다.")
			.category(frontend)
			.build());
	}

	private Post published(Post post, int daysAgo) {
		Instant publishedAt = clock.instant().minus(Duration.ofDays(daysAgo));
		post.publish(publishedAt);
		return post;
	}
}
