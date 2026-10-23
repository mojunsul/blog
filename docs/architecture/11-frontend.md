## 11. 프론트엔드 구조

**App Router 규칙 요약**
- 폴더 = URL 경로, `page.tsx` = 해당 경로의 화면, `layout.tsx` = 공유 레이아웃
- `[slug]` = 동적 경로, `(group)` = URL에 나타나지 않는 레이아웃 그룹
- Next.js 16부터 기존 `middleware.ts`는 `proxy.ts`로 이름이 바뀌었다. 이 프로젝트는 쓰지 않는다 ('관리자 인증').

```
frontend/src/
├ app/
│  ├ layout.tsx                     # 최상위 HTML, 폰트, 기본 metadata
│  ├ (public)/
│  │  ├ layout.tsx                  # 블로그 헤더 (푸터는 M5)
│  │  ├ not-found.tsx               # 공개 페이지의 notFound() 화면 (헤더 포함)
│  │  ├ page.tsx                    # /                  글 목록
│  │  ├ posts/[slug]/page.tsx       # /posts/{slug}      글 상세 + Giscus
│  │  ├ categories/[slug]/page.tsx  # /categories/{slug}
│  │  ├ tags/[name]/page.tsx        # /tags/{name}
│  │  └ search/page.tsx             # /search?q=
│  ├ admin/
│  │  ├ login/page.tsx              # /admin/login
│  │  └ (dashboard)/
│  │     ├ layout.tsx               # 관리자 레이아웃 (AdminAuthGuard로 감쌈, 메뉴·로그아웃)
│  │     ├ page.tsx                 # /admin → /admin/posts 이동 (redirect)
│  │     ├ posts/page.tsx           # /admin/posts
│  │     ├ posts/new/page.tsx       # /admin/posts/new
│  │     ├ posts/[id]/edit/page.tsx # /admin/posts/{id}/edit
│  │     └ categories/page.tsx      # /admin/categories
│  ├ not-found.tsx                  # (M5) 어느 경로에도 맞지 않는 URL
│  ├ sitemap.ts
│  ├ robots.ts
│  └ rss.xml/route.ts
├ components/
│  ├ ui/            # Button, Input 등 공통
│  ├ post/          # PostCard, PostList, Pagination, TagBadge(M4)
│  ├ markdown/      # MarkdownRenderer (서버 컴포넌트)
│  └ admin/         # LoginForm, AdminAuthGuard, LogoutButton, AdminPostList, PostForm, MarkdownPreview
├ lib/
│  ├ api/
│  │  ├ server.ts   # 서버 컴포넌트용 fetch (API_INTERNAL_URL, server-only)
│  │  ├ client.ts   # (M2) 브라우저용 fetch (/api/v1, CSRF 헤더, 401 시 refresh 후 재시도)
│  │  ├ errors.ts   # ProblemDetail 타입(명세에 없어 직접 정의)·파싱, ApiError
│  │  ├ posts.ts
│  │  ├ auth.ts     # 브라우저용 login·logout·getMe (client.ts 사용)
│  │  └ admin-posts.ts # 브라우저용 관리자 글 API (getAdminPosts, deletePost, client.ts 사용)
│  ├ cookie.ts      # 쿠키 문자열에서 값 읽기 (readCookie)
│  ├ next-path.ts   # 로그인 후 이동 경로 해석 (parseNextParam)
│  ├ single-flight.ts # 동시 호출을 한 번의 실행으로 합침 (singleFlight)
│  ├ date.ts        # UTC → KST 표시 변환 (formatDate, YYYY.MM.DD)
│  ├ pagination.ts  # URL의 page 쿼리 해석 (parsePageParam)
│  ├ post-status.ts # URL의 status 쿼리 해석 (parseStatusParam)
│  ├ slug.ts        # slug 형식 검사 (isValidSlug, 6장 slug 규칙)
│  └ utils.ts
├ schemas/
│  ├ auth.ts        # loginSchema - 서버 검증 규칙과 동일
│  └ post.ts        # postSchema, categorySchema - 서버 검증 규칙과 동일
├ types/
│  └ api.d.ts       # openapi-typescript 생성 파일 (직접 수정 금지)
```

### 렌더링 전략
- **공개 페이지**: 서버 컴포넌트로 요청마다 렌더링하고, 아래 두 가지만 캐시한다. 공개 페이지의 캐시 기준은 이 항목에만 두고, 다른 곳은 여기를 가리킨다.
  - 캐시하는 것
    - 데이터 함수(`lib/api/posts.ts`): `'use cache'` + `cacheLife({ revalidate: 60 })`. `server.ts`에는 캐시를 넣지 않는다.
    - `MarkdownRenderer` 컴포넌트: `'use cache'` + `cacheLife('max')` (이유는 'Markdown 렌더링'). 컴포넌트 단위 캐시는 여기에만 둔다.
  - 캐시하지 않는 것: 페이지 전체. 응답은 빌드 때 만든 정적 셸(`Suspense` fallback 포함)과, 위 캐시로 그 자리에서 렌더링한 본문 스트리밍이다 (Next 16.4의 `next start`·standalone에서 확인). Partial Prefetching의 ISR 업그레이드는 `generateStaticParams`가 돌려준 `params`에만 적용되는데, 빌드 시 API 비의존 때문에 그 함수를 쓰지 않는다.
    - 본문은 같은 응답 안에서 스트리밍되어 검색엔진이 받는 HTML에 들어 있다. 매 요청 비용은 캐시된 결과로 컴포넌트를 렌더링하는 정도라 작은 서버에서도 충분하다고 본다. 운영에서 느리면 측정 후 다시 검토한다.
  - `next.config.ts`의 `cacheComponents: true`(create-next-app 기본값) 모드라 캐시 주기는 `export const revalidate`나 `fetch`의 `next.revalidate`가 아니라 `cacheLife`로 정한다. 구현 전 `node_modules/next/dist/docs/`의 caching 문서를 확인한다.
  - `'use cache'` 함수·컴포넌트를 요청 시점 API(`params`, `searchParams`, `connection()`) 없이 호출하면 빌드 때 실행되어 정적 셸에 들어간다(= 빌드 중 백엔드 호출). 반드시 요청 시점 API를 거친 뒤 호출한다.
- **관리자 페이지**: 클라이언트 컴포넌트, 브라우저에서 `/api/v1` 직접 호출.
- 화면 접근 제어는 `AdminAuthGuard`(클라이언트)가 하는 이동 편의용이다. **실제 권한 검증은 Spring Security가 담당한다.** 자세한 내용은 '관리자 인증'.
- 관리자가 글을 저장·수정·삭제해도 공개 페이지는 바로 바뀌지 않는다(본문이 바뀌면 `MarkdownRenderer`의 캐시 키도 바뀐다). 즉시 반영(on-demand revalidate)은 M5 이후 검토한다.
  - 데이터 캐시는 stale-while-revalidate다: `revalidate` 60초가 지난 뒤 첫 요청은 이전 내용을 받으면서 백그라운드 갱신을 시작하고, 그다음 요청부터 새 내용을 받는다. `expire`를 정하지 않아(만료 없음) 요청이 없으면 갱신도 없으므로 반영 지연에 상한이 없다.
  - 사이트 안 링크 이동은 클라이언트 캐시(`stale`, 기본 5분)를 먼저 쓰므로 더 늦게 보일 수 있다. 새로고침은 이 캐시를 거치지 않는다.

### 빌드 시 API 의존 제거
CI에서 프론트 이미지를 빌드할 때는 백엔드가 없다. `next build`가 데이터 fetch를 하는 페이지를 미리 렌더링하면 빌드가 실패한다.
- 데이터를 가져오는 페이지(홈, 목록, 상세, sitemap, RSS)는 **빌드 시점에 프리렌더링하지 않고** 요청 시 렌더링한다 (캐시 기준은 '렌더링 전략'. Cache Components 모드에서는 요청 시점 데이터를 `Suspense` 안에서 읽는다).
- `generateStaticParams`는 사용하지 않는다.
- `next.config`에 `output: 'standalone'`을 설정해 Docker 이미지를 경량화한다.

### API 주소 처리
- 서버 컴포넌트: 환경변수 `API_INTERNAL_URL` (로컬 `http://localhost:8080`, 서버 `http://backend:8080`)
  - `NEXT_PUBLIC_`이 아니므로 빌드 때 박히지 않고 실행 시점에 읽힌다. 이미지 하나를 환경변수만 바꿔 쓴다.
  - 값이 없으면 기본값으로 대신하지 않고, 호출 시점에 명확한 오류를 낸다 (운영 설정 누락이 엉뚱한 주소 연결로 숨지 않게).
  - 로컬: `frontend/.env.example`(커밋, 값 `http://localhost:8080`)을 `.env.local`로 복사해 쓴다. 운영: `docker-compose.prod.yml`의 `environment`에 직접 적는다 (비밀값 아님).
  - `server.ts`는 `import 'server-only'`로 클라이언트 번들에 들어가지 않게 한다.
- 브라우저: 항상 상대 경로 `/api/v1` (M2부터. M1 공개 페이지는 서버 컴포넌트만 호출한다)
  - 로컬(`next dev`)에서는 `next.config`의 `rewrites`로 `/api/:path*` → `${API_INTERNAL_URL}/api/:path*`. 운영은 Nginx가 `/api`를 백엔드로 보내므로(3장) 두지 않는다: `rewrites`는 빌드 때 라우트 목록에 고정되어 실행 시점 환경변수를 읽지 못하고, `output: 'standalone'` 이미지에서 Nginx와 이중으로 라우팅하게 된다. 그래서 `process.env.NODE_ENV === 'development'`일 때만 규칙을 돌려준다.
  - 목적지는 서버 컴포넌트와 같은 `API_INTERNAL_URL`을 쓴다 (주소를 한 곳에 둔다). 값이 없으면 `server.ts`와 같은 이유로 기본값 없이 오류를 내되, 검사는 `rewrites()`가 개발 분기(`NODE_ENV === 'development'`) 안에서 규칙을 돌려줄 때만 한다. 모듈 최상위나 개발이 아닌 분기에서는 검사하지 않고, 개발이 아니면 환경변수가 없어도 빈 규칙을 돌려준다 (CI의 `next build`·`next typegen`에는 이 환경변수도 백엔드도 없다, '빌드 시 API 의존 제거').

### 에러 처리
- 2xx가 아닌 응답은 `ApiError`(`status`, `code`, `detail`, `errors`)로 바꾼다. 본문이 ProblemDetail이면 파싱하고, 아니면(Nginx 502 HTML 등) `code: null`과 일반 메시지를 넣는다.
- 네트워크 오류(백엔드 다운 등)는 감싸지 않고 그대로 던져 Next 오류 화면으로 보낸다.
- 페이지에서 `ApiError`의 `status === 404`면 `notFound()`를 호출한다.
  - 단, `'use cache'` 함수 안에서 던진 오류는 캐시 경계를 넘으며 일반 `Error`가 되어 `instanceof ApiError`와 `status`를 잃는다. 그래서 캐시하는 단건 조회 함수(`getPost`)는 404를 함수 안에서 `null`로 바꿔 돌려주고, 페이지는 `null`이면 `notFound()`를 호출한다. 그 외 오류는 그대로 던진다 (오류 화면으로 가므로 `status`가 없어도 된다).

### 관리자 인증
**접근 제어: `proxy.ts` 대신 클라이언트 가드**
- `proxy.ts`로 `access_token` 쿠키 유무를 검사할 수 없다. `access_token`은 `Path=/api`, `refresh_token`은 `Path=/api/v1/auth`라(10-1) 브라우저가 `/admin/**` 페이지 요청에 실어 보내지 않는다. 검사하면 로그인해도 항상 쿠키가 없는 것으로 보여 `/admin/login`으로 되돌려 보낸다.
- 쿠키 `Path`를 `/`로 넓히거나 `Path=/`인 로그인 표시 쿠키를 새로 두는 방법은 쓰지 않는다. 앞의 것은 모든 공개 페이지 요청에 JWT가 실리고(10-6 제약), 뒤의 것은 백엔드 변경이 필요한데 얻는 것은 이동 편의뿐이다 (실제 검증은 어차피 Spring Security).
- 그래서 `proxy.ts`를 만들지 않고, `admin/(dashboard)/layout.tsx`가 클라이언트 컴포넌트 `AdminAuthGuard`로 `children`을 감싼다.
  - `getMe()`(`GET /api/v1/auth/me`)를 마운트 때 한 번 부른다. 성공하기 전까지는 `children`을 그리지 않고 "확인 중..." 문구만 보여, 하위 화면의 API 호출이 인증 확인 전에 나가지 않게 한다.
  - 401은 `client.ts`가 재발급과 재시도를 거친 뒤 로그인 화면으로 이동시키므로('`client.ts`') 가드는 따로 다루지 않는다. 그 외 오류(5xx, 네트워크)는 "로그인 상태를 확인하지 못했습니다. 새로고침해 주세요." 문구를 보여 준다 (`children`은 그리지 않는다).
  - 레이아웃은 클라이언트 이동 중에 유지되므로 확인은 관리자 화면에 들어올 때 한 번이다. 이후 Access 토큰이 만료되면 각 API 호출의 401 처리로 이어진다.
  - 로그인 화면(`/admin/login`)은 `(dashboard)` 밖이라 가드를 거치지 않는다. 이미 로그인한 상태에서 들어와도 이동시키지 않고 폼을 보여 준다 (다시 로그인하면 쿠키가 새로 발급된다, 8-3).
- 확인되지 않은 화면의 껍데기(서버가 렌더링한 "확인 중...")는 인증 없이 받을 수 있지만 데이터는 없다. 데이터는 모두 `/api/v1/admin/**`에서 오고 거기서 막힌다.

**`client.ts`** (`lib/api/client.ts`, 브라우저 전용)
- 시그니처: `apiFetch<T>(path, { method, body })`. `path`는 `/api/v1` 뒤의 경로(`/auth/me`, `/admin/posts`)이고, 요청 주소는 항상 `/api/v1${path}`다. `method` 기본값 `GET`. `body`가 있으면 `JSON.stringify`하고 `Content-Type: application/json`을 붙인다. 쿠키는 같은 출처 요청이라 `fetch` 기본값으로 실린다.
- 응답: `204`면 `undefined`, 그 외 2xx는 JSON. 2xx가 아니면 `parseApiError`로 만든 `ApiError`를 던진다 ('에러 처리'). 네트워크 오류는 감싸지 않고 그대로 던진다.
- CSRF 헤더: `GET`·`HEAD`가 아닌 요청은 `XSRF-TOKEN` 쿠키 값(`readCookie(document.cookie, "XSRF-TOKEN")`)을 `X-XSRF-TOKEN` 헤더로 보낸다. 쿠키가 없으면 먼저 `GET /api/v1/auth/csrf`를 불러 받는다 (세션 쿠키라 브라우저를 다시 열면 사라진다). 이 호출은 `csrf` 응답이 `204`라 `apiFetch`를 거치지 않고 `fetch`로 직접 하고, 실패는 원래 요청을 보내지 않고 그대로 던진다: 2xx가 아니면(Nginx 502, 429 등) `parseApiError`로 만든 `ApiError`를, 호출이 끝났는데도 `XSRF-TOKEN` 쿠키가 없으면 `ApiError`(`status` 0이 아닌 응답이므로 호출의 상태 코드, `code: null`)를, 네트워크 오류는 감싸지 않고 던진다. 헤더 없이 보내 `403`으로 만들지 않는다. 호출자에게는 `ApiError`면 "그 외 오류"(로그인 결과 표 참고)로 보인다. 쿠키는 서버가 내려 준다. 이 호출은 `singleFlight`로 합치지 않는다. 쿠키가 없는 채로 상태 변경 요청이 동시에 나가는 경우가 없고(첫 상태 변경 요청은 로그인 제출이다), 겹쳐도 각 요청이 쿠키를 받은 직후 그 값을 읽어 헤더로 쓰므로 서버가 보는 쿠키와 헤더는 같다.
- 401 처리 (재발급 대상: `/auth/login`, `/auth/refresh`를 뺀 모든 요청)
  1. 응답이 401이면 `POST /auth/refresh`를 부른다. 동시에 여러 요청이 401을 받아도 재발급은 한 번만 보내고 나머지는 그 결과를 기다린다 (`singleFlight`, 10-3). 재발급이 끝나면(성공이든 실패든) 다음 401은 새 재발급을 시작한다.
  2. 재발급이 `2xx`면 원래 요청을 **한 번만** 다시 보낸다.
  3. 재발급이 실패했거나(`401` `INVALID_REFRESH_TOKEN` 등 2xx가 아님) 다시 보낸 요청도 401이면 `/admin/login?next={현재 경로+쿼리}`로 `window.location.assign`한다 (전체 새로고침이라 메모리의 상태와 열려 있던 폼 입력은 사라진다. 로그인이 풀린 뒤라 받아들인다). 이동 중에도 호출자는 401 `ApiError`를 받는다.
  - 재발급 요청 자체의 네트워크 오류는 그대로 던진다 (이동하지 않는다).
  - `/auth/login`의 401(`INVALID_CREDENTIALS`)은 재발급 없이 호출자에게 전달해 폼이 메시지를 보여 준다. `/auth/refresh`는 위 흐름이 직접 부른다.
- `403 INVALID_CSRF_TOKEN`은 재시도하지 않는다. 헤더를 쿠키에서 읽어 보내므로 쿠키가 있는 한 일치하고, 쿠키가 없으면 위에서 먼저 받는다. 이 오류가 나면 예상 밖 상황이라 `ApiError`로 드러낸다.
- 테스트: `client.ts`는 `document`·`window`·`fetch`에 기대어 순수 함수가 아니라 단위 테스트하지 않는다. 안에서 쓰는 `readCookie`·`singleFlight`·`parseNextParam`을 단위 테스트하고, 흐름은 아래 화면 확인으로 본다.

**`lib/` 순수 함수**
- `readCookie(cookieString, name): string | null`: `document.cookie` 형식(`a=1; b=2`)에서 이름이 **정확히 같은** 쿠키의 값을 돌려준다. 없거나 값이 빈 문자열이면 `null`, 같은 이름이 여러 개면 첫 값, 값에 `=`가 있으면 첫 `=` 뒤를 모두 값으로 본다. 디코딩하지 않는다 (Spring이 내리는 토큰은 UUID라 인코딩이 없다).
- `singleFlight(fn): () => Promise<T>`: 반환 함수를 호출하면 진행 중인 실행이 있으면 그 Promise를 돌려주고, 없으면 `fn`을 실행한다. 성공이든 거절이든 끝나면 진행 중 상태를 비워 다음 호출이 새로 실행한다.
- `parseNextParam(raw: string | null): string`: 로그인 후 이동 경로를 정한다. `raw`가 `/admin` 자신이거나 `/admin/`, `/admin?`으로 시작하고, `/admin/login`(자기 자신으로의 반복 이동)으로 시작하지 않으며, `\`·제어 문자가 없으면 그 값을, 그 외(`null`, 빈 값, `//evil.com`, `https://...`, `/administrator`, `/posts/1` 등)는 기본값 `/admin`을 돌려준다. 외부 주소로 보내는 열린 리다이렉트를 막기 위해 같은 출처의 `/admin` 아래 경로만 허용한다.

**로그인 화면 (`/admin/login`)**
- `page.tsx`는 서버 컴포넌트로 제목과 `LoginForm`(클라이언트, `components/admin/`)만 둔다. 블로그 헤더가 있는 `(public)` 밖이라 헤더가 없다.
- `loginSchema`(`schemas/auth.ts`, Zod)는 서버 규칙(8-3 `LoginRequest`)과 같다.
  | 필드 | 규칙 | 메시지 |
  |---|---|---|
  | username | 앞뒤 공백을 지우지 않고, 공백만 있는 값 거절, 50자 이하 | "아이디를 입력하세요", "아이디는 50자 이하로 입력하세요" |
  | password | 빈 값 거절, 공백만 있는 값 허용, 길이 상한 없음 | "비밀번호를 입력하세요" |
  - 값은 서버로 그대로 보낸다 (트림하지 않는다, 서버가 정확히 같은 값만 맞다고 본다).
  - password 상한이 없는 이유: 72바이트 초과는 서버가 인증 실패로 처리하는 입력이라 폼에서 막지 않는다.
  - "공백만 있는 값"의 판정은 JS `trim()`과 Java `@NotBlank`가 달라(NBSP 등) 프론트가 약간 더 엄격할 수 있다. 서버 검증이 최종이다.
- 폼: react-hook-form + `zodResolver(loginSchema)`. 제출 중에는 버튼을 막아 중복 제출을 방지한다. 입력에 `autoComplete`(`username`, `current-password`)를 단다.
- 제출 결과
  | 결과 | 화면 |
  |---|---|
  | 성공 | `router.replace(parseNextParam(new URLSearchParams(location.search).get("next")))` |
  | `401` `INVALID_CREDENTIALS` | 폼 위에 "아이디 또는 비밀번호가 올바르지 않습니다." (아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않는다, 8-3) |
  | 그 외 `ApiError`(400, 429, 5xx, csrf 호출 실패 등)·네트워크 오류 | "로그인하지 못했습니다. 잠시 후 다시 시도해 주세요." |
  - `next`는 제출 시점에 `window.location.search`에서 `URLSearchParams.get`으로 읽는다: 같은 키가 여러 개면 **첫 값**만 쓰고 나머지는 무시하며, 키가 없으면 `null`이다. `parseNextParam`은 그 한 값만 검사한다 (첫 값이 잘못됐으면 뒤 값이 유효해도 `/admin`) (`useSearchParams`는 `Suspense` 경계가 필요해지고, 제출 핸들러에서만 쓰므로 필요 없다).
  - 400은 Zod가 같은 규칙으로 먼저 막으므로 거의 나오지 않는다. 필드별 서버 오류 표시는 하지 않고 위 일반 문구로 둔다.

**화면 확인** (백엔드 `local` 프로필 + Postgres + 관리자 계정 환경변수로 `next dev`를 띄워 DevTools로 본다. 완료 조건의 확인 수단이다)
1. 로그아웃 상태에서 `/admin/posts` 접근 → `/admin/login?next=%2Fadmin%2Fposts`로 이동 (`Network`에 `auth/me` 401 → `auth/refresh` 401)
2. 잘못된 비밀번호 → "아이디 또는 비밀번호가 올바르지 않습니다.", 이동·새로고침 없음. 올바른 값 → `/admin`(→ `/admin/posts`) (`next`가 있으면 그 경로)
3. `XSRF-TOKEN` 쿠키를 지우고 로그인 → `auth/csrf` 호출이 로그인 요청 앞에 나가고 로그인이 성공 (`403` 없음)
4. 로그인한 뒤 `access_token` 쿠키만 지우고 `/admin` 새로고침 → `auth/me` 401 → `auth/refresh` 204 → `auth/me` 200으로 화면 표시 (로그인 화면으로 가지 않음)
5. `access_token`·`refresh_token`을 모두 지우고 `/admin/posts` 새로고침 → `/admin/login?next=%2Fadmin%2Fposts`
6. 로그아웃 버튼 → `/admin/login`, 두 쿠키 삭제, 다시 `/admin` 접근 시 1번과 같음
7. `/admin/login?next=//evil.com`, `?next=https://evil.com`, `?next=//evil.com&next=/admin/posts`(첫 값이 잘못됨)로 로그인 → `/admin`(→ `/admin/posts`)으로 이동. `?next=/admin/posts&next=//evil.com`(첫 값이 유효)은 `/admin/posts`
8. `.env.local` 없이 `API_INTERNAL_URL`을 비운 채 `npm run build`와 `npx next typegen` 성공 (CI와 같은 조건)
- 동시 요청이 재발급을 한 번만 보내는 것은 `singleFlight` 단위 테스트로 본다 (화면에서는 재현하기 어렵다. 관리자 글 목록은 가드의 `auth/me`가 끝난 뒤 목록을 부르므로 요청이 겹치지 않는다. 4번은 '관리자 글 목록'의 화면 확인에서 `/admin/posts`로 다시 본다).

**로그아웃**
- 위치는 관리자 레이아웃의 메뉴 영역이다 ('관리자 글 목록'). `LogoutButton`이 `logout()`(`lib/api/auth.ts`, `POST /auth/logout`)을 부르고, 성공하면 `window.location.assign("/admin/login")`로 이동한다 (전체 새로고침으로 화면의 인증 상태를 모두 버린다).
- 로그아웃은 서버가 인증 없이 받고 쿠키가 없어도 `204`라(8-3) 401 처리는 필요 없다. 실패(네트워크, 5xx, 429)하면 이동하지 않고 버튼 옆에 "로그아웃하지 못했습니다. 다시 시도해 주세요."를 보여 준다 (`role="alert"`). 버튼은 요청 중에만 막고 실패 뒤 다시 누를 수 있다. 다시 누르면 문구를 지운다.

### 관리자 글 목록 (`/admin/posts`)
**레이아웃** (`admin/(dashboard)/layout.tsx`, 서버 컴포넌트)
- `AdminAuthGuard` 안에 헤더와 `children`을 둔다. 헤더도 인증 확인 뒤에만 보인다.
- 헤더: 메뉴 링크 "글"(`/admin/posts`), "카테고리"(`/admin/categories`, 화면은 #78)와 `LogoutButton`('로그아웃'). 현재 메뉴 강조는 하지 않는다 (메뉴가 둘이고, 강조하려면 `usePathname` 때문에 헤더가 클라이언트 컴포넌트가 된다).
- `/admin`(`page.tsx`)은 `redirect("/admin/posts")`만 한다. 로그인 후 기본 이동 경로(`parseNextParam`의 `/admin`)는 그대로 두고 여기서 넘긴다. 리다이렉트가 가드보다 먼저라 로그아웃 상태의 `/admin`은 `/admin/login?next=%2Fadmin%2Fposts`로 간다.

**URL** (`/admin/posts?status=S&page=N`)
- `page`: 공개 목록('글 목록 페이지')과 같은 규칙이다. `parsePageParam`으로 해석하고 1부터 세며 API에는 `page - 1`, 첫 페이지 링크는 `page`를 뺀다. 크기는 API 기본값(10).
- `status`: `lib/post-status.ts`의 순수 함수 `parseStatusParam(value: string | string[] | undefined)`가 해석한다. 없으면 전체(`undefined`), 정확히 `DRAFT`·`PUBLISHED`면 그 값, 그 외(`?status=`, `draft`, 앞뒤 공백, 없는 값, 같은 키 여러 개)는 잘못된 값(`null`)이다.
  - API(8-4)보다 엄격한 이유는 `page`와 같다: 화면 링크는 정해진 값만 만들고, 사용자가 고친 URL은 API를 부르기 전에 거른다.
- 링크 주소는 `status`(있을 때), `page`(2 이상일 때) 순서의 쿼리로 만든다 (`/admin/posts?status=DRAFT&page=2`). `Pagination`의 `pageHref`도 이 규칙으로 현재 `status`를 유지한다.
- 잘못된 `page`·`status`는 API를 부르지 않고 "잘못된 주소입니다."와 "글 목록으로"(`/admin/posts`) 링크를 보여 준다. `notFound()`를 쓰지 않는다: 관리자 화면은 색인 대상이 아니고, 관리자 레이아웃 안에서 바로 목록으로 돌아갈 수 있게 한다.

**구성**
- `posts/page.tsx`(서버 컴포넌트): `Suspense`(fallback "불러오는 중...") 안의 비동기 컴포넌트가 `searchParams`를 읽어 위 두 함수로 해석하고, 유효하면 `AdminPostList`(클라이언트, `components/admin/`)에 `page`, `status`를 넘긴다. 공개 목록과 같은 해석 함수를 같은 입력 형태로 쓰기 위해서다 (`useSearchParams`는 `URLSearchParams`라 형태가 다르다). 데이터는 서버에서 부르지 않는다 ('렌더링 전략').
- `AdminPostList`는 `useEffect`에서 `getAdminPosts({ page: page - 1, status })`를 부르고, `page`·`status`가 바뀌면 다시 부른다. 앞선 요청의 응답이 늦게 오면 effect 정리 함수에서 표시한 값으로 버린다. 화면 하나라 데이터 패칭 라이브러리(SWR, TanStack Query)는 쓰지 않는다.
- `lib/api/admin-posts.ts`(`client.ts` 사용, 타입은 `api.d.ts`의 `PageResponseAdminPostSummaryResponse`)
  - `getAdminPosts({ page, status })`: `GET /admin/posts?page=…&status=…` (`status`가 없으면 넣지 않는다)
  - `deletePost(id)`: `DELETE /admin/posts/{id}`
- `useEffect` 안의 오류는 Next 오류 화면으로 가지 않으므로 네트워크 오류도 아래 표대로 화면에서 다룬다 ('에러 처리'의 "그대로 던진다"는 서버 컴포넌트 기준).

**화면**
- 제목 "글"과 "새 글" 링크(`/admin/posts/new`, 화면은 #77). 그 아래 안내 한 줄: "저장·삭제한 내용은 1분이 지난 뒤 공개 페이지를 열면 갱신되며, 한 번 더 새로고침해야 보일 수 있습니다." ('렌더링 전략'의 반영 지연. 방문이 없으면 갱신되지 않는 것까지는 적지 않는다: 관리자는 보통 직접 열어 확인한다)
- 상태 필터: 링크 "전체"·"작성 중"·"발행됨"(`/admin/posts`, `?status=DRAFT`, `?status=PUBLISHED`, 바꾸면 첫 페이지). 현재 필터는 `aria-current="page"`로 표시한다.
- 표 열: 제목(`/admin/posts/{id}/edit` 링크, 화면은 #77), 상태(`DRAFT` "작성 중", `PUBLISHED` "발행됨"), 카테고리(이름, 없으면 "-"), 발행일(`formatDate`, `publishedAt`이 `null`이면 "-"), 수정일(`formatDate(updatedAt)`), 삭제 버튼. 순서는 API 정렬(`id` 내림차순) 그대로다.
- `Pagination`은 공개 목록과 같은 컴포넌트에 위 링크 규칙의 `pageHref`를 넘긴다.
- 상태별 표시 (제목·필터·안내는 항상 보인다)
  | 상태 | 화면 |
  |---|---|
  | 불러오는 중 (처음, `page`·`status` 변경, 삭제 뒤 다시 불러올 때 모두) | "불러오는 중..." (이전 표는 지운다. 그래서 다시 불러오는 동안에는 삭제 버튼이 없다) |
  | 첫 페이지 `content`가 빔 | "글이 없습니다." |
  | 2페이지 이상인데 `content`가 빔 | "이 페이지에는 글이 없습니다."와 "첫 페이지로" 링크(현재 `status` 유지) |
  | `401` | `client.ts`가 로그인 화면으로 이동시키므로 "불러오는 중..."을 유지한다 (`AdminAuthGuard`와 같다) |
  | 그 외 `ApiError`(400, 403, 429, 5xx)·네트워크 오류 | "글 목록을 불러오지 못했습니다. 새로고침해 주세요." |

**삭제**
- 버튼을 누르면 `window.confirm("\"{제목}\" 글을 삭제할까요? 되돌릴 수 없습니다.")`로 묻고, 취소하면 요청하지 않는다. 관리자 1명이라 확인 모달 컴포넌트를 따로 만들지 않는다.
- 요청 중에는 모든 삭제 버튼을 막아 중복 요청을 막는다.
  | 결과 | 화면 |
  |---|---|
  | `204` | 현재 `page`·`status`로 목록을 다시 불러온다 |
  | `404` (`POST_NOT_FOUND`, 다른 탭 등에서 이미 삭제) | 바라던 상태라 문구 없이 목록을 다시 불러온다 |
  | `401` | `client.ts`가 로그인 화면으로 이동시킨다 (문구 없음) |
  | 그 외 `ApiError`(403, 429, 5xx, csrf 호출 실패)·네트워크 오류 | `window.alert("삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.")`, 목록은 그대로 둔다 |
- 다시 불러오기(`204`·`404` 뒤)의 표시와 실패는 위 상태별 표를 따른다 (실패하면 "글 목록을 불러오지 못했습니다. 새로고침해 주세요.").
- 마지막 페이지의 마지막 글을 지우면 다시 불러온 페이지가 비어 "이 페이지에는 글이 없습니다." 화면이 된다. 앞 페이지로 자동 이동하지 않는다.

**화면 확인** ('관리자 인증'의 화면 확인과 같은 환경. 1번은 글이 없는 DB에서 보고, 그 뒤 글을 DRAFT·PUBLISHED를 섞어 11개 이상 `POST /api/v1/admin/posts`로 만든다. "불러오는 중..."과 버튼 막기는 DevTools `Network` 스로틀링(Slow 4G 등)으로 본다. 완료 조건의 확인 수단이다)
1. 로그인 후 `/admin` → `/admin/posts`. 헤더의 "글"·"카테고리"·로그아웃, 제목·필터·공개 반영 안내 문구, "글이 없습니다."가 보인다. 로그아웃 상태의 `/admin` → `/admin/login?next=%2Fadmin%2Fposts`
2. 목록이 `id` 내림차순 10개, "다음"으로 `?page=2`. 필터 "작성 중"·"발행됨"이 그 상태만 보이고 `page`가 빠진다. 2페이지에서 "이전"·"다음" 링크가 `status`를 유지한다. 발행한 적 없는 DRAFT의 발행일은 "-"
3. `?page=0`, `?page=abc`, `?status=draft`, `?status=`, `?status=DRAFT&status=PUBLISHED` → "잘못된 주소입니다.", `Network`에 `admin/posts` 요청 없음. `?page=999` → "이 페이지에는 글이 없습니다."와 "첫 페이지로"(`/admin/posts`), `?status=DRAFT&page=999`의 "첫 페이지로"는 `/admin/posts?status=DRAFT`
4. confirm 문구에 그 글의 제목이 들어간다. 삭제 취소 → 요청 없음. 확인 → `DELETE` `204` 뒤 목록 다시 불러옴 (스로틀링 시 `DELETE` 중 모든 삭제 버튼이 막히고, 다시 불러오는 동안 "불러오는 중..."). 다른 탭에서 먼저 지운 글을 삭제 → `404`, 문구 없이 목록 갱신. DevTools로 `admin/posts/*` 요청을 막고 삭제 → alert. `admin/posts?*`(목록 조회)만 막고 삭제 → `204` 뒤 "글 목록을 불러오지 못했습니다. 새로고침해 주세요."
5. DevTools로 `admin/posts?*` 요청을 막고 새로고침 → "글 목록을 불러오지 못했습니다. 새로고침해 주세요." 스로틀링 시 필터·페이지 링크로 이동하면 응답 전까지 이전 표 대신 "불러오는 중..."
6. `access_token`만 지우고 `/admin/posts` 새로고침 → `auth/me` 401 → `auth/refresh` 204 → `auth/me` 200 → `admin/posts` 200
7. 스로틀링 시 로그아웃 요청 중 버튼이 막힌다. DevTools로 `auth/logout`을 막고 로그아웃 → 오류 문구, 막기를 풀고 다시 누르면 문구가 지워지고 `/admin/login`
8. `.env.local` 없이 `npm run build`와 `npx next typegen` 성공 ('관리자 인증' 8번과 같다)

### 날짜 표시
- KST `YYYY.MM.DD` (`2026.10.11`). 내장 `Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })`의 `formatToParts`로 조립한다 (`timeZone`을 명시해 서버 시간대와 무관하게 동작).
- 날짜 라이브러리(dayjs, date-fns)는 쓰지 않는다. 상대 시간("3일 전"), 기간 계산, 문자열 파싱이 필요해지면 다시 검토한다.
- 잘못된 입력은 `RangeError`를 그대로 던진다 (API의 `publishedAt`은 PUBLISHED 글에서 항상 있음).

### 글 목록 페이지 (`/`)
- URL은 `/?page=N`이고, 사용자에게 보이는 값이라 `page`는 **1부터** 센다. API를 호출할 때 `page - 1`로 바꾼다.
  - 첫 페이지 링크는 `/`(파라미터 없음)로 한다. `?page=1`로 들어와도 첫 페이지를 보여 주고 리다이렉트하지 않는다.
  - 페이지 크기는 API 기본값(10)을 쓰고 URL로 받지 않는다.
- `page` 해석은 `lib/pagination.ts`의 순수 함수 `parsePageParam`이 한다. 값이 없으면 1, `^[1-9][0-9]*$`에 맞고 10000 이하면 그 수, 그 외(`0`, 음수, 소수, 문자, 10000 초과, 같은 키가 여러 개)는 잘못된 값이다.
  - 상한은 API `page` 범위(7장)에 맞춘 것이다. API를 호출하기 전에 걸러, 범위 밖 값이 API 400으로 오류 화면이 되지 않게 한다. API의 400을 `notFound()`로 바꾸지는 않는다 (프론트가 잘못된 파라미터를 보내는 버그가 404로 가려진다).
- 잘못된 `page`, 범위를 넘은 `page`(2 이상인데 `content`가 비어 있음)는 `notFound()`. 첫 페이지로 보정하지 않는다 (API와 같은 기준이고, 같은 내용이 여러 URL로 색인되지 않게).
  - `searchParams`를 `Suspense` 안에서 읽으므로 응답은 이미 스트리밍 중이라 상태 코드는 `200`으로 남고 Next가 `noindex`를 넣는다. 실제 `404` 상태가 필요하면 `proxy.ts`에서 미리 걸러야 하는데, 화면과 색인 결과가 같아 하지 않는다.
- 구성: `page.tsx`는 `Suspense`(fallback은 짧은 로딩 문구)만 두고, 그 안의 비동기 컴포넌트가 `searchParams`를 읽어 `getPosts(page)`(`lib/api/posts.ts`, `'use cache'` + `cacheLife({ revalidate: 60 })`)를 호출한다. 캐시는 인자(`page`)별로 나뉜다.
- `PostCard` 표시 항목: 제목(`/posts/{slug}` 링크), 요약(있을 때), 카테고리 이름(있을 때, 텍스트만. 카테고리 페이지는 M4), 발행일(`formatDate`).
  - 썸네일은 M1에서 표시하지 않는다 (업로드는 M6이고, 외부 URL은 `next/image` 허용 도메인 설정이 필요하다).
  - 태그는 M4 전까지 항상 `[]`이므로 표시하지 않는다.
- 페이지 이동(`Pagination`): "이전"·"다음" 링크와 현재 위치(`2 / 3`)만 둔다. 링크 주소는 `pageHref(page)` prop으로 받는다 (이 페이지는 위의 `/`·`/?page=N` 규칙, 관리자 목록은 '관리자 글 목록'). "이전"은 `page > 1`, "다음"은 `hasNext`일 때 보이고, 전체가 한 페이지 이내면 영역을 숨긴다. 페이지 번호 목록은 글이 많아지면 검토한다.
- 글이 하나도 없으면(첫 페이지 `content`가 빔) "아직 작성된 글이 없습니다." 문구를 보여 준다.
- 레이아웃: `app/page.tsx`를 `app/(public)/page.tsx`로 옮기고, `(public)/layout.tsx`에 블로그 이름(`/` 링크) 헤더를 둔다. 푸터는 넣을 내용(RSS 링크 등)이 생기는 M5에서 추가한다.

### 글 상세 페이지 (`/posts/{slug}`)
- 구성: 목록 페이지와 같다. `page.tsx`는 `Suspense`만 두고, 그 안의 비동기 컴포넌트가 `params`를 읽어 `getPost(slug)`(`lib/api/posts.ts`, `'use cache'` + `cacheLife({ revalidate: 60 })`)를 호출한다.
- `getPost`를 호출하기 전에 `lib/slug.ts`의 순수 함수 `isValidSlug`로 6장 slug 규칙(`^[a-z0-9]+(?:-[a-z0-9]+)*$`, 200자 이하)을 검사하고, 맞지 않으면 `notFound()`.
  - 규칙에 맞는 값은 영문 소문자·숫자·하이픈뿐이라 그대로 API 경로에 넣는다. 규칙 밖 값을 그대로(또는 `encodeURIComponent`로) 넣으면 `.`이 든 값은 URL 정규화로 다른 API 경로가 될 수 있고(`encodeURIComponent`는 `.`을 인코딩하지 않는다), 인코딩된 슬래시(`%2F`)는 서버가 404가 아닌 400으로 거절할 수 있어 오류 화면이 된다.
  - `/posts/..`·`/posts/%2E%2E`는 브라우저가 요청 전에 `/`로 정규화하므로 상세 페이지에 오지 않는다(목록 페이지가 열린다). 검사는 값이 어떤 경로로 들어왔는지와 무관하게 `params`에 온 값에 적용한다.
  - 8-1의 "형식이 다른 slug는 404"와 결과가 같다 (API는 형식을 검사하지 않고, 프론트는 호출 전에 걸러 같은 not-found를 보여 준다). Next가 `params`의 `%2F`·`%2E`를 디코딩하든 안 하든 `%`·`/`·`.`은 규칙에 맞지 않으므로 결과가 같다.
  - 관리자 폼의 Zod 스키마(M2)도 이 함수의 패턴을 함께 쓴다.
- `getPost`는 404(없는 글, DRAFT)면 `null`을 돌려주고('에러 처리'), 페이지는 `null`이면 `notFound()`. 그 외 오류는 그대로 던진다.
  - 목록 페이지와 같은 이유로 상태 코드는 `200`으로 남고 `noindex`가 붙는다.
- 표시 항목: 제목(`h1`), 카테고리 이름(있을 때, 텍스트만)과 발행일(`formatDate`)을 `PostCard`와 같은 형식으로, 본문(`MarkdownRenderer`).
  - 요약·썸네일·태그·수정일은 표시하지 않는다. 요약은 목록과 M5의 OG 설명용이고, 썸네일·태그는 목록 페이지와 같은 이유다.
  - 글별 `<title>`·OG 태그(`generateMetadata`)는 M5에서 추가한다. Giscus도 M5.
- 캐시: 기준은 '렌더링 전략'. 같은 글을 다시 요청하면 `getPost`와 `MarkdownRenderer`의 캐시로 백엔드 호출과 Shiki 하이라이팅이 다시 일어나지 않는다.
- not-found 화면: `app/(public)/not-found.tsx`를 둔다. 공개 페이지에서 부른 `notFound()`(상세의 없는 글, 목록의 잘못된 `page`)가 블로그 헤더 안에서 "페이지를 찾을 수 없습니다." 문구와 `/` 링크로 표시된다.
  - 어느 경로에도 맞지 않는 URL(`/foo`)은 루트 `app/not-found.tsx`가 맡는데, 루트 레이아웃만 거쳐 헤더가 없다. M5의 not-found 페이지 작업에서 함께 정하고, 그 전까지는 Next 기본 404 화면을 쓴다.

### Markdown 렌더링
- `components/markdown/MarkdownRenderer`는 `content`(Markdown 문자열)만 받는 **서버 컴포넌트**다. react-markdown의 `MarkdownAsync`를 쓴다.
  - `@shikijs/rehype`는 하이라이터를 비동기로 만드는 플러그인이라 동기 컴포넌트(`Markdown`)에서는 쓸 수 없다. 서버에서 렌더링하므로 Shiki와 문법 파일이 브라우저 번들에 들어가지 않는다.
- 플러그인: `remarkPlugins: [remarkGfm]`(표, 체크리스트, 취소선), `rehypePlugins: [rehypeSlug, [rehypeShiki, 옵션]]`.
- Shiki 옵션
  - `themes: { light: 'github-light', dark: 'github-dark' }`. 사이트가 `prefers-color-scheme`으로 다크 모드를 따르므로, `globals.css`에서 같은 미디어 쿼리로 `--shiki-dark`, `--shiki-dark-bg` 변수 색으로 바꾼다.
  - `langs: []` + `lazy: true`: 코드 블록에 나온 언어만 처음 쓸 때 불러온다. 기본값은 내장 언어 전체를 한 번에 불러와 작은 서버의 기동 후 첫 렌더링 시간과 메모리를 쓴다. 언어 목록을 따로 관리하지 않아도 된다.
  - `defaultLanguage: 'text'`, `fallbackLanguage: 'text'`: 언어를 안 적었거나 모르는 언어(오타 등)여도 오류 없이 같은 코드 블록 모양으로 표시한다.
- 보안: raw HTML은 react-markdown 기본 동작(이스케이프해서 글자로 표시)을 유지한다. `rehype-raw`, `skipHtml` 변경, `urlTransform` 재정의는 하지 않는다 (기본 `urlTransform`이 `javascript:` 링크를 막는다).
- 스타일: `@tailwindcss/typography`를 쓴다 (Tailwind preflight가 제목·목록·표 기본 스타일을 지우므로). 설정은 `globals.css`의 `@plugin "@tailwindcss/typography";`.
  - `MarkdownRenderer`가 결과를 `<div className="prose dark:prose-invert max-w-none">`로 감싸서 반환한다. react-markdown에는 `className` prop이 없고, 본문 스타일은 렌더러가 책임져야 호출하는 페이지마다 같은 클래스를 반복하지 않는다.
  - `max-w-none`으로 `prose`의 기본 최대 너비(65ch)를 끄고, 본문 너비는 호출하는 페이지의 레이아웃이 정한다.
- 이미지·링크는 기본 `<img>`, `<a>`로 둔다. `next/image` 변환, 외부 링크 새 탭 열기, 제목 앵커 링크·목차는 필요해지면 검토한다 (`rehype-slug`의 `id`가 그 기반이다).
- 확인용 데이터: `LocalDataInitializer`의 시드 글에 raw HTML(`<script>`, `<img onerror>`)과 `javascript:` 링크, 언어 없는·모르는 언어 코드 블록을 넣어, 로컬에서 표·체크리스트·하이라이팅과 함께 안전하게 표시되는지 본다.
- 캐시(`'use cache'` + `cacheLife('max')`, 정의는 '렌더링 전략')를 두는 이유
  - 상세 페이지는 매 요청 렌더링되므로('렌더링 전략'), 캐시하지 않으면 요청마다 Shiki가 실행된다. 또 Shiki는 토큰화할 때 `Date.now()`를 불러, 캐시 밖에서 실행되면 Next가 요청마다 `encountered the unstable value Date.now()` 로그를 남긴다. `connection()`으로 요청 시점 렌더링을 확정하면 로그는 없어지지만 요청마다 Shiki가 실행된다.
  - 결과가 인자 `content`에만 달려 있고 인자가 캐시 키가 되므로, 글을 고치면 새 키가 된다. 같은 본문을 다시 하이라이팅할 이유가 없어 가장 긴 프로필(`max`)을 쓴다. 글 수정 반영은 `getPost`의 데이터 캐시('렌더링 전략'의 반영 지연)를 따른다.
- `MarkdownRenderer`는 async 서버 컴포넌트라 단위 테스트하지 않는다. 렌더링 결과는 글 상세 페이지에서 시드 글로 확인한다.

### 프론트엔드 테스트
- Vitest로 `lib/`의 로직이 있는 순수 함수만 단위 테스트한다 (`date.ts`, `errors.ts`의 파싱 함수, `pagination.ts`, `post-status.ts`, `slug.ts`, `cookie.ts`, `next-path.ts`, `single-flight.ts`).
  - `async` 서버 컴포넌트는 Vitest·Jest 모두 단위 테스트를 지원하지 않으므로 단위 테스트하지 않는다.
  - `server.ts`는 `server-only`가 테스트 환경에서 import 시 오류를 던지므로 단위 테스트하지 않는다 (실제 호출은 화면에서 확인).
  - `client.ts`와 `auth.ts`·`admin-posts.ts`(브라우저 전역·`fetch`에 기대는 파일), `loginSchema`(라이브러리 규칙을 그대로 선언)는 단위 테스트하지 않는다. 흐름은 '관리자 인증'·'관리자 글 목록'의 화면 확인으로 본다.
- 최소 구성: `vitest`, `vitest.config.mts`(`resolve: { tsconfigPaths: true }`로 `@/` 별칭 해석, Vite 내장 기능이라 `vite-tsconfig-paths` 플러그인은 쓰지 않는다), `"test": "vitest run"`. React Testing Library·jsdom은 컴포넌트 테스트가 필요해질 때 추가한다.
- `vitest`는 정확한 버전으로 고정한다(`^` 없음). Vitest는 메이저가 7~9개월마다 나와 깨지는 변경이 있으므로, 메이저 업그레이드는 별도 이슈로 한다.

### API 타입 생성
```json
"scripts": {
  "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts"
}
```
- **실행**: 백엔드를 `local` 프로필로 띄운 상태(Postgres 필요, Swagger는 `local`에서만 켜진다)에서 `npm run gen:api`를 실행하고 `src/types/api.d.ts`를 커밋한다. 직접 수정하지 않는다. CI에 백엔드가 없어도 프론트가 빌드되도록 파일을 커밋한다 (2-3).
- **버전**: `openapi-typescript`는 정확한 버전으로 고정한다(`^` 없음, devDependency). 버전이 바뀌면 같은 명세에서도 출력이 달라져 재생성 diff에 섞이므로, 올리는 일은 별도 이슈로 한다.
- **린트**: 생성 파일은 ESLint 대상에서 뺀다(`eslint.config.mjs`의 `globalIgnores`에 `src/types/api.d.ts`). 타입 검사(`tsc`)는 그대로 거친다.
- **타입 사용**: 별칭 파일을 두지 않는다 (`lib/api/types.ts`는 삭제). 사용하는 파일에서 `import type { components } from "@/types/api"`로 받고, 쓰는 스키마만 파일 상단에 한 줄 별칭으로 둔다 (`type PostSummary = components["schemas"]["PostSummaryResponse"];`). 스키마 이름(`...Response`)과 화면에서 부르는 이름이 달라도 파일 안에서만 보이는 별칭이라 충분하고, 공용 별칭 파일은 명세 변경을 한 번 더 따라가야 하는 두 번째 목록이 되기 때문이다.
  - 제네릭 `PageResponse<T>`는 springdoc이 `PageResponsePostSummaryResponse`처럼 스키마마다 펼쳐 놓는다. 그 스키마를 그대로 쓰고 제네릭 별칭은 만들지 않는다.
  - 날짜는 `format: date-time`이어도 생성 타입이 `string`이다. 지금처럼 `string`으로 받고 `formatDate`로 변환한다.
  - 명세에 없는 `ProblemDetail`(`code`, `errors` 확장)은 `errors.ts`에 직접 정의한다. 오류 응답은 이미 `Partial<ProblemDetail>`로 방어적으로 파싱하고(Nginx HTML 등 ProblemDetail이 아닌 응답이 올 수 있다), 에러 응답을 명세에 싣지 않는다 (5장).
- **명세가 실제 응답과 다를 때**: 프론트 타입을 손으로 고치지 않고 백엔드에서 고친다 (5장 'OpenAPI 명세'). 고친 뒤 `gen:api`를 다시 실행한다.
- **재생성 누락 방지**: 자동 검사(CI)는 두지 않는다. 프론트 CI에는 백엔드·DB가 없고, 검사하려면 백엔드 CI에서 앱을 띄워 명세를 뽑아 커밋된 파일과 비교해야 해 관리자 1명인 블로그에는 비용이 크다. 대신 컨트롤러 시그니처나 응답 DTO를 바꾸는 PR은 같은 PR에서 `gen:api`를 다시 실행해 커밋한다. 누락으로 타입이 어긋난 일이 반복되면 CI 검사를 도입한다.
- **확인**: 생성 직후 `npx tsc --noEmit`이 통과해야 한다. 응답 필드의 `?`(선택)가 남아 있으면 5장의 `required` 규칙이 빠진 것이다.

---

