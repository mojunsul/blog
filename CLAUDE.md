# Blog

개인 개발 블로그 모노레포. Spring Boot(`backend/`) + Next.js(`frontend/`), 인프라는 `infra/`(아직 비어 있음).

## 먼저 읽을 것
- 설계 기준 문서: @docs/architecture.md — 구현과 충돌하면 **문서를 먼저 수정**하고 구현한다.
- 프론트엔드 작업 전: @frontend/AGENTS.md — 이 Next.js는 학습 데이터와 다르다. `frontend/node_modules/next/dist/docs/`를 먼저 확인한다.

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
- 테스트는 메서드 이름(한국어)으로 설명하고 주석을 달지 않는다.
- 주석은 한국어로 쓴다.

## Git
- `main` 직접 push 금지. 브랜치: `feat/#이슈번호-설명`.
- 커밋: Conventional Commits. 접두사(`feat:`, `fix:` 등)는 영어, **제목과 본문은 한국어**.
  - 제목은 한 줄로 간결하게(`feat: 공통 예외 처리 추가`), 본문은 필요할 때만 `-` 목록으로.
- 이슈 하나 = PR 하나. 마일스톤(M0~M6)은 문서 13장.
