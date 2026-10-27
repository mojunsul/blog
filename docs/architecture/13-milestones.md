## 13. 마일스톤

| 단계 | 내용 | 완료 기준 |
|---|---|---|
| **M0. 세팅** | 모노레포, Spring/Next 초기화, 로컬 PostgreSQL(Docker), Flyway V1, ProblemDetail 기반 공통 예외 처리, Swagger(local), Actuator, Testcontainers 테스트 기반, CI 워크플로, 브랜치 보호 | 로컬에서 양쪽 실행, `/actuator/health` UP, CI 통과 |
| **M1. 글 조회** | Post·Category 엔티티, 공개 목록/상세 API, Next 목록/상세 페이지, Markdown + Shiki 렌더링, UTC→KST 표시, 빌드 시 API 비의존 설정 | 시드 글이 코드 하이라이팅과 함께 표시, API 없이 `next build` 성공 |
| **M2. 관리자** | AdminInitializer, Security + JWT + CSRF, 로그인/재발급/로그아웃/me, 관리자 글·카테고리 CRUD, 관리자 화면, PostForm(react-hook-form + Zod), openapi-typescript 연동 | 로그인 후 글 작성·수정·삭제 가능, CSRF 토큰 없는 요청은 403 |
| **M3. 배포** | Dockerfile(backend, frontend standalone), prod compose(로그 제한, healthcheck), Nginx 리버스 프록시·보안 헤더·rate limit, Certbot HTTPS, 도메인 연결, DB 백업, deploy 워크플로 | `https://blog.도메인` 접속, main 머지 시 자동 배포, 백업 파일이 Object Storage에 생성되고 복구 테스트 완료 |
| **M4. 분류/검색** | 태그(PostTag, 정규화), QueryDSL 검색·필터, 카테고리·태그·검색 페이지 | 카테고리·태그·키워드로 글 탐색 |
| **M5. 부가 기능** | Giscus, sitemap/robots/RSS, 글별 metadata(OG 태그), not-found 페이지, Umami, Uptime Kuma(서비스 다운·인증서 만료 알림, 12-3 '갱신') | 검색엔진 등록 가능, 댓글 동작, 장애 알림 수신 |
| **M6. 고도화** | 조회수(별도 API, 중복 방지), 이미지 업로드(Object Storage), Redis(캐시·Refresh 토큰), on-demand revalidate, 무중단 배포, 테스트 보강 | 선택 |

**원칙**
- 테스트는 M1부터 기능과 함께 작성한다. 계층별로 분리한다: Repository는 `@DataJpaTest` + Testcontainers, Service는 Mockito 단위 테스트(분기·예외가 있을 때), Controller는 `@WebMvcTest`, 인증·권한 흐름은 `@SpringBootTest` 통합 테스트.
- M0 → M3까지 먼저 완료해 배포된 상태를 만든 뒤 기능을 확장한다.
- 각 마일스톤은 GitHub 이슈 여러 개로 나누고, 이슈 하나 = PR 하나를 기본으로 한다.

---

