## 2. 저장소 만들기

### 2-1. 저장소 방식: 모노레포
프론트·백엔드·인프라를 **하나의 저장소**에서 관리한다.
- 저장소 이름 예시: `blog`
- 공개 여부: **Public** (포트폴리오). 비밀번호, 비밀번호 해시, 시크릿, `.env`는 절대 커밋하지 않는다.

### 2-2. 생성 절차
1. GitHub에서 저장소 생성 (README 체크, `.gitignore`는 직접 작성)
2. 로컬로 clone 후 디렉터리 생성
   ```bash
   mkdir backend frontend infra docs .github
   ```
3. **백엔드 생성** (Spring Initializr → `backend/`에 압축 해제)
   - Gradle, Java 21
   - Group: `io.github.mojunsul`, Artifact: `blog`, Package: `io.github.mojunsul.blog`
   - Dependencies: Spring Web, Validation, Spring Data JPA, PostgreSQL Driver, Spring Security, Flyway Migration, Spring Boot Actuator, Lombok, Testcontainers, (선택) DevTools
   - `build.gradle`에 직접 추가
     - `springdoc-openapi-starter-webmvc-ui` (Spring Boot 버전과 호환되는 springdoc 버전 확인)
     - JWT: `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`
     - QueryDSL: `io.github.openfeign.querydsl` 포크 (annotation processor 설정 포함)
4. **프론트엔드 생성** (`frontend/`에서)
   ```bash
   npx create-next-app@latest .
   # TypeScript: Yes / ESLint: Yes / Tailwind CSS: Yes
   # src/ directory: Yes / App Router: Yes / import alias: @/*
   ```
   - 설치된 Next.js 버전을 확인하고, 이 문서의 Next.js 관련 내용(특히 `proxy.ts`)이 버전과 맞는지 점검한다.
5. 루트 `.gitignore` 작성 (2-3)
6. 첫 커밋: `chore: initialize monorepo with backend and frontend`
7. GitHub 설정: `main` 브랜치 보호 (PR 필수, CI 통과 필수)

### 2-3. 루트 .gitignore 핵심 항목
```gitignore
# env / secrets
.env
.env.*
!.env.example

# backend
backend/build/
backend/.gradle/
backend/out/

# frontend
frontend/node_modules/
frontend/.next/
frontend/out/

# IDE / OS
.idea/
.vscode/
*.iml
.DS_Store
```
> `frontend/src/types/api.d.ts`(자동 생성 타입)는 **커밋한다.** CI에서 백엔드 없이 프론트 빌드가 가능해야 하기 때문.

### 2-4. 브랜치·커밋 규칙 (GitHub Flow)
- `main`: 항상 배포 가능한 상태, 직접 push 금지
- 작업 브랜치: `feat/#이슈번호-설명`, `fix/#...`, `refactor/#...`, `chore/#...`
  - 예: `feat/#3-post-list-api`
- 흐름: 이슈 → 브랜치 → 구현 → PR → 리뷰(Copilot) → `main` 머지 → 자동 배포
- 커밋 메시지: Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`, `ci:`)
- 이슈는 마일스톤(M0~M6) 단위로 묶고, **이슈 하나 = PR 하나**를 기본으로 한다.

---

