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

## Git
- `main` 직접 push 금지. 브랜치: `feat/#이슈번호-설명`.
- 커밋: Conventional Commits. 접두사(`feat:`, `fix:` 등)는 영어, **제목과 본문은 한국어**.
  - 제목은 한 줄로 간결하게(`feat: 공통 예외 처리 추가`), 본문은 필요할 때만 `-` 목록으로.
- 이슈 하나 = PR 하나. 마일스톤(M0~M6)은 문서 13장.
