# Blog

개인 개발 블로그 모노레포. Spring Boot(`backend/`) + Next.js(`frontend/`), 인프라는 `infra/`(아직 비어 있음).

## 먼저 읽을 것
- 설계 기준 문서: @docs/architecture.md — 구현과 충돌하면 **문서를 먼저 수정**하고 구현한다.
  - 사용자가 "구현 세션"이라고 밝힌 세션은 문서를 직접 고치지 않는다. 충돌을 발견하면 구현을 멈추고 해당 이슈에 코멘트로 남긴 뒤 사용자에게 알린다. 문서 수정은 설계 세션에서 한다.
  - 구현 세션은 시작할 때 기능 브랜치를 pull 받아 설계 세션이 올린 최신 문서 커밋을 확인한다.
  - 사용자가 "검증 세션"이라고 밝힌 세션은 "#N 검증해줘" 요청마다 이슈 #N의 기능 브랜치(이슈 본문에 적힌 브랜치, 없으면 `feat/#N-*`, 못 찾거나 여럿이면 묻는다)를 검증한다. 로컬 브랜치가 원격보다 앞서 있으면 push 전 로컬 커밋을 검증하고, 검증한 커밋 SHA와 원격 대비 상태(ahead/behind)를 보고에 적는다. 요청끼리 독립적이라 이전 검증의 결론을 끌어오지 않는다.
    - 기준(우선순위 순): 이슈 본문 → 그 브랜치의 `docs/architecture.md` → 그 브랜치의 CLAUDE.md 규칙.
    - 절차: `git fetch` 후 브랜치를 스크래치패드 worktree에 체크아웃(저장소 폴더의 브랜치는 바꾸지 않는다) → `origin/main...브랜치` diff를 이슈의 할 일·완료 조건·테스트 항목과 하나씩 대조 → `./gradlew build` 직접 실행(프론트 변경 시 lint·typegen·tsc·build도, CI 결과는 머지 조건이라 확인하지 않는다) → "통과 / 위반(파일:줄 + 어긴 규칙·문서 위치) / 확인 못 함"으로 보고 → worktree 삭제.
    - 코드·문서를 고치지 않고, 머지·브랜치 삭제·저장소 설정 변경도 하지 않는다. 보고 내용을 사용자에게 먼저 확인받은 뒤 이슈에 코멘트로 남긴다. 위반이 없으면 검증한 커밋 SHA, 빌드 결과, "위반 없음"과 확인 못 함·참고만 짧게, 위반이 있으면 자세히 쓴다. 설계 문제면 설계 세션에서 처리하도록 알린다.
- 프론트엔드 작업 전: @frontend/AGENTS.md — 이 Next.js는 학습 데이터와 다르다. `frontend/node_modules/next/dist/docs/`를 먼저 확인한다.

## 판단 기준
- 규칙·설계를 정할 때와 코드를 구현할 때 모두 두 가지를 함께 따진다.
  - 현재 맥락에 맞는가: 관리자 1명인 개인 블로그, 단일 소형 서버.
    - 1인 프로젝트라 단순함을 우선하되, 실무 연습용 프로젝트이기도 하다. 규모상 필요 없더라도 실무에서 익혀 둘 가치가 있는 방식이면 택할 수 있다.
  - 한국 기업에서 널리 쓰는 방식인가: 실무에서 통용되는 관례를 우선하고, 벗어나면 이유를 남긴다.
    - 구현에서는 국내 Spring·Next.js 실무에서 흔한 패턴(계층 구조, DTO 변환, 예외 처리, 테스트 작성 방식 등)을 우선한다. 덜 알려진 기법을 쓰면 PR 설명이나 "왜" 주석에 이유를 남긴다.

## 명령어
```bash
# backend (Java 21)
cd backend && ./gradlew bootRun
cd backend && ./gradlew test
cd backend && ./gradlew spotlessApply   # 포맷 적용 (CI는 build에 포함된 spotlessCheck로 검사)

# frontend (Node >= 20.9 필수)
cd frontend && npm run dev
cd frontend && npm run lint && npx next typegen && npx tsc --noEmit
cd frontend && npm run build
```

## 구조 메모
- 백엔드 패키지: `io.github.mojunsul.blog`, 설정 파일은 `application.yaml`(`.yml` 아님).
- 프론트엔드는 `src/` 사용 (`src/app`, import alias `@/*` → `src/*`).
- `next.config.ts`에 `cacheComponents: true` — ISR은 `revalidate` export가 아니라 `'use cache'` + `cacheLife`.
- API는 같은 도메인의 `/api/v1`. 시간은 UTC 저장·응답, KST 변환은 프론트.

## 규칙
- 스키마 변경은 Flyway 마이그레이션 파일로만 (`ddl-auto: validate`). 적용된 마이그레이션은 수정하지 않는다.
- 엔티티를 API 응답으로 직접 반환하지 않는다. DTO는 `record`.
- 엔티티 생성은 `private` 생성자에 `@Builder` (클래스에 붙이지 않는다). 기본 생성자는 `@NoArgsConstructor(access = PROTECTED)`.
- 검증은 서버에서 반드시 다시 한다 (프론트 Zod와 별개).
- 토큰은 HttpOnly 쿠키만 사용, localStorage 금지. CSRF 보호를 끄지 않는다.
- Markdown 렌더링에 `rehype-raw` 금지.
- 저장소는 Public: `.env`, 비밀번호, 시크릿, 해시를 커밋하지 않는다. 환경변수 목록은 `infra/.env.example`(값 비움).
- `frontend/src/types/api.d.ts`는 생성 파일이지만 커밋한다. 직접 수정 금지 (`npm run gen:api`).

## 포맷
- 백엔드는 네이버 핵데이 Java 컨벤션(탭, 120자). 커밋 전 `./gradlew spotlessApply`. 설정은 `backend/config/naver/`(직접 수정 금지).
- 프론트엔드 포맷 도구는 아직 없다 (ESLint만).

## 주석
- 기본은 주석 없이 이름으로 의도를 드러낸다. 주석을 달기 전에 이름부터 고친다.
- 주석은 **"왜"만** 쓴다: 코드만 봐서는 알 수 없는 이유, 제약, 설계 결정, 우회. 코드가 하는 일("무엇")을 반복하지 않는다.
- 문서(설계 문서 섹션 번호 등)를 주석에서 참조하지 않는다. 근거는 문서에 두고 주석에는 이유만 쓴다.
- 임시 코드는 언제 바뀌는지 적는다 (예: `// M0 임시 설정: M2에서 JWT·CSRF 기반으로 교체`).
- 할 일 주석은 `// TODO(#이슈번호): 내용` 형식으로, 이슈 번호를 반드시 단다.
- Javadoc은 위 "왜" 기준에 해당할 때만 단다. 모든 public 요소에 달지 않는다.
- 주석 처리한 코드는 남기지 않는다.
- 주석은 한국어로 쓴다.

## 테스트
- 이름은 한국어 메서드명으로 쓴다 (`void 다시_발행해도_최초_발행_시각은_바뀌지_않는다()`). `@DisplayName`은 쓰지 않는다.
- `// given`, `// when`, `// then` 주석으로 구간을 나눈다. 실행과 검증이 붙으면 `// when & then`. 그 외 주석은 달지 않는다.
- 계층별 방식
  - 엔티티·도메인: 스프링 없는 단위 테스트
  - Repository: `RepositoryTestSupport`(`@DataJpaTest` + Testcontainers). 직접 정의한 쿼리만 테스트한다 (`save`, `findById` 등 기본 메서드 제외).
  - Service: `@ExtendWith(MockitoExtension.class)` 단위 테스트. 분기·예외가 있는 메서드만 테스트한다.
  - Controller: `@WebMvcTest` + `@MockitoBean` Service. 요청 검증, 응답 형식, 상태 코드를 확인한다.
  - 인증·권한 흐름: `IntegrationTestSupport`(`@SpringBootTest`) 통합 테스트.
- 쿼리는 Service 테스트에서 mock되므로 반드시 Repository 테스트에서 검증한다.
- 테스트 데이터는 `fixture/`의 엔티티별 Fixture 클래스로 만든다. 테스트에는 그 테스트에 중요한 값만 넘긴다.
- AssertJ(`assertThat`)와 BDDMockito(`given`)를 쓴다.

## Git
- `main` 직접 push 금지. 브랜치: `feat/#이슈번호-설명`.
- 커밋: Conventional Commits. 접두사(`feat:`, `fix:` 등)는 영어, **제목과 본문은 한국어**.
  - 제목은 한 줄로 간결하게(`feat: 공통 예외 처리 추가`), 본문은 필요할 때만 `-` 목록으로.
- 이슈 하나 = PR 하나. 마일스톤(M0~M6)은 문서 13장.
- 기능 이슈의 설계 문서 변경은 별도 PR로 나누지 않는다. 설계 세션이 기능 브랜치에 문서를 먼저 커밋하고, 구현 세션이 같은 브랜치에서 구현해 한 PR로 머지한다. 기능과 무관한 문서·규칙 변경만 `docs/#이슈번호-설명` 브랜치로 따로 올린다.
