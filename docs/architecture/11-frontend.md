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
│  │  ├ layout.tsx                  # 블로그 헤더·검색 입력·푸터(RSS 링크), RSS alternate
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
│  ├ sitemap.ts                    # /sitemap.xml ('sitemap·robots')
│  ├ robots.ts                     # /robots.txt
│  └ rss.xml/route.ts              # /rss.xml ('RSS 피드·푸터')
├ components/
│  ├ ui/            # Button, Input 등 공통
│  ├ post/          # PostCard, PostList, Pagination, TagBadge, Comments(Giscus, 클라이언트 컴포넌트)
│  ├ markdown/      # MarkdownRenderer (서버 컴포넌트)
│  └ admin/         # LoginForm, AdminAuthGuard, LogoutButton, AdminPostList, PostForm, MarkdownPreview, AdminCategoryManager, CategoryForm
├ lib/
│  ├ api/
│  │  ├ server.ts   # 서버 컴포넌트용 fetch (API_INTERNAL_URL, server-only)
│  │  ├ client.ts   # (M2) 브라우저용 fetch (/api/v1, CSRF 헤더, 401 시 refresh 후 재시도)
│  │  ├ errors.ts   # ProblemDetail 타입(명세에 없어 직접 정의)·파싱, ApiError
│  │  ├ posts.ts
│  │  ├ taxonomy.ts # 서버 컴포넌트용 카테고리·태그 목록 (getPublicCategories, getPublicTags)
│  │  ├ auth.ts     # 브라우저용 login·logout·getMe (client.ts 사용)
│  │  ├ admin-posts.ts # 브라우저용 관리자 글 API (getAdminPosts, getAdminPost, createPost, updatePost, deletePost, client.ts 사용)
│  │  └ admin-categories.ts # 브라우저용 카테고리 API (getCategories, createCategory, updateCategory, deleteCategory, client.ts 사용)
│  ├ cookie.ts      # 쿠키 문자열에서 값 읽기 (readCookie)
│  ├ next-path.ts   # 로그인 후 이동 경로 해석 (parseNextParam)
│  ├ single-flight.ts # 동시 호출을 한 번의 실행으로 합침 (singleFlight)
│  ├ date.ts        # UTC → KST 표시 변환 (formatDate, YYYY.MM.DD)
│  ├ pagination.ts  # URL의 page 쿼리 해석 (parsePageParam)
│  ├ post-status.ts # URL의 status 쿼리 해석 (parseStatusParam)
│  ├ post-id.ts     # URL의 글 id 경로 해석 (parsePostIdParam)
│  ├ search.ts      # URL의 q 쿼리 해석·검색 페이지 주소 (parseSearchQuery, searchHref)
│  ├ taxonomy.ts    # 카테고리·태그 페이지 주소와 태그 경로 해석 (categoryHref, tagHref, parseTagNameParam)
│  ├ site.ts        # 사이트 이름·설명·주소 (SITE_NAME, SITE_DESCRIPTION, SITE_OPEN_GRAPH, siteUrl, server-only)
│  ├ sitemap.ts     # sitemap 항목 만들기 (buildSitemap)
│  ├ rss.ts         # RSS XML 만들기 (buildRss, RSS_PATH, RSS_ALTERNATE_TYPES)
│  ├ slug.ts        # slug 형식 검사 (isValidSlug, SLUG_PATTERN·MAX_SLUG_LENGTH, 6장 slug 규칙)
│  └ utils.ts
├ schemas/
│  ├ auth.ts        # loginSchema - 서버 검증 규칙과 동일
│  ├ post.ts        # postSchema - 서버 검증 규칙과 동일 (tags 제외)
│  └ category.ts    # categorySchema - 서버 검증 규칙과 동일
├ types/
│  └ api.d.ts       # openapi-typescript 생성 파일 (직접 수정 금지)
```

### 렌더링 전략
- **공개 페이지**: 서버 컴포넌트로 요청마다 렌더링하고, 아래 두 가지만 캐시한다. 공개 페이지의 캐시 기준은 이 항목에만 두고, 다른 곳은 여기를 가리킨다.
  - 캐시하는 것
    - 데이터 함수(`lib/api/posts.ts`·`lib/api/taxonomy.ts`): `'use cache'` + `cacheLife({ revalidate: 60 })`. `server.ts`에는 캐시를 넣지 않는다. 검색(`searchPosts`)은 캐시하지 않는다 (이유는 '검색 페이지').
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
- 데이터를 가져오는 페이지(홈, 목록, 상세, 검색, sitemap, RSS)는 **빌드 시점에 프리렌더링하지 않고** 요청 시 렌더링한다 (캐시 기준은 '렌더링 전략'. Cache Components 모드에서는 요청 시점 데이터를 `Suspense` 안에서 읽는다).
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
6. 로그아웃 버튼 → `/admin/login`, 두 쿠키 삭제, 다시 `/admin` 접근 시 `/admin/login?next=%2Fadmin` (`Network`는 1번과 같음)
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
- `/admin`(`page.tsx`)은 `redirect("/admin/posts")`만 한다. 로그인 후 기본 이동 경로(`parseNextParam`의 `/admin`)는 그대로 두고 여기서 넘긴다. 이 `redirect`는 `children` 자리에서 렌더링될 때 동작하는데(응답은 200이고 RSC 페이로드로 이동한다) 가드가 인증 전에 `children`을 그리지 않으므로, 로그아웃 상태의 `/admin`은 가드의 401 처리로 `/admin/login?next=%2Fadmin`으로 간다. 로그인 뒤 `/admin`으로 돌아와 `/admin/posts`로 넘어가므로 결과는 같아, `next.config.ts` `redirects()`로 렌더링 전에 넘기지 않는다.

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

**화면 확인** ('관리자 인증'의 화면 확인과 같은 환경. local 프로필은 시작할 때 글이 없으면 시드 글을 넣으므로, 1번 전에 시드 글을 모두 `DELETE /api/v1/admin/posts/{id}`로 지워 글이 없는 상태를 만들고, 1번을 볼 때까지 백엔드를 재시작하지 않는다. 그 뒤 글을 DRAFT·PUBLISHED를 섞어 11개 이상 `POST /api/v1/admin/posts`로 만든다. "불러오는 중..."과 버튼 막기는 DevTools `Network` 스로틀링(Slow 4G 등)으로 본다. 완료 조건의 확인 수단이다)
1. 로그인 후 `/admin` → `/admin/posts`. 헤더의 "글"·"카테고리"·로그아웃, 제목·필터·공개 반영 안내 문구, "글이 없습니다."가 보인다. 로그아웃 상태의 `/admin` → `/admin/login?next=%2Fadmin`, 로그인하면 `/admin/posts`
2. 목록이 `id` 내림차순 10개, "다음"으로 `?page=2`. 필터 "작성 중"·"발행됨"이 그 상태만 보이고 `page`가 빠진다. 2페이지에서 "이전"·"다음" 링크가 `status`를 유지한다. 발행한 적 없는 DRAFT의 발행일은 "-"
3. `?page=0`, `?page=abc`, `?status=draft`, `?status=`, `?status=DRAFT&status=PUBLISHED` → "잘못된 주소입니다.", `Network`에 `admin/posts` 요청 없음. `?page=999` → "이 페이지에는 글이 없습니다."와 "첫 페이지로"(`/admin/posts`), `?status=DRAFT&page=999`의 "첫 페이지로"는 `/admin/posts?status=DRAFT`
4. confirm 문구에 그 글의 제목이 들어간다. 삭제 취소 → 요청 없음. 확인 → `DELETE` `204` 뒤 목록 다시 불러옴 (스로틀링 시 `DELETE` 중 모든 삭제 버튼이 막히고, 다시 불러오는 동안 "불러오는 중..."). 다른 탭에서 먼저 지운 글을 삭제 → `404`, 문구 없이 목록 갱신. DevTools로 `admin/posts/*` 요청을 막고 삭제 → alert. `admin/posts?*`(목록 조회)만 막고 삭제 → `204` 뒤 "글 목록을 불러오지 못했습니다. 새로고침해 주세요."
5. DevTools로 `admin/posts?*` 요청을 막고 새로고침 → "글 목록을 불러오지 못했습니다. 새로고침해 주세요." 스로틀링 시 필터·페이지 링크로 이동하면 응답 전까지 이전 표 대신 "불러오는 중..."
6. `access_token`만 지우고 `/admin/posts` 새로고침 → `auth/me` 401 → `auth/refresh` 204 → `auth/me` 200 → `admin/posts` 200
7. 스로틀링 시 로그아웃 요청 중 버튼이 막힌다. DevTools로 `auth/logout`을 막고 로그아웃 → 오류 문구, 막기를 풀고 다시 누르면 문구가 지워지고 `/admin/login`
8. `.env.local` 없이 `npm run build`와 `npx next typegen` 성공 ('관리자 인증' 8번과 같다)

### 관리자 글 작성·수정 (`/admin/posts/new`, `/admin/posts/{id}/edit`)
**구성**
- `posts/new/page.tsx`(서버 컴포넌트): 제목 "새 글"과 `PostForm`(클라이언트, `components/admin/`)을 둔다.
- `posts/[id]/edit/page.tsx`(서버 컴포넌트): 제목 "글 수정"과, '관리자 글 목록'처럼 `Suspense`(fallback "불러오는 중...") 안의 비동기 컴포넌트가 `params`를 읽는다. `lib/post-id.ts`의 순수 함수 `parsePostIdParam(value: string): number | null`로 해석해 유효하면 `PostForm`에 `id`를 넘기고, `null`이면 API를 부르지 않고 "잘못된 주소입니다."와 "글 목록으로"(`/admin/posts`) 링크를 보여 준다 ('관리자 글 목록'과 같은 이유로 `notFound()`를 쓰지 않는다).
  - `parsePostIdParam`: `^[1-9][0-9]*$`에 맞고 `Number.isSafeInteger`인 값이면 그 수, 그 외(`0`, 앞에 `0`이 붙은 값, 음수, 소수, 문자, `2^53` 이상)는 `null`. 값이 API 경로에 그대로 들어가므로 숫자만 통과시킨다 ('글 상세 페이지'의 slug 검사와 같은 이유). `2^53` 이상은 Long 범위 안이라도 JS 수로 정확히 표현되지 않고 글 수로 닿을 수 없는 값이라 잘못된 주소로 본다.
- `PostForm({ id?: number })`: 마운트 때 카테고리 목록과, `id`가 있으면 글을 함께(`Promise.allSettled`) 불러온 뒤 폼을 그린다. 불러오는 동안·실패는 아래 '불러오기' 표를 따른다. 두 요청이 모두 끝난 뒤 판정해, 응답 순서와 상관없이 결과가 같다. 성공하면 `reset(글에서 만든 값)`(새 글은 기본값)으로 폼의 기준값을 정하고, 이 기준값이 이탈 경고의 `isDirty` 기준이 된다. 데이터 패칭 라이브러리는 쓰지 않는다 ('관리자 글 목록'과 같다).
- API (`client.ts` 사용, 타입은 `api.d.ts`의 `PostRequest`·`AdminPostDetailResponse`·`PostCreateResponse`·`CategoryResponse`)
  - `lib/api/admin-posts.ts`에 더한다: `getAdminPost(id)`(`GET /admin/posts/{id}`), `createPost(body)`(`POST /admin/posts`), `updatePost(id, body)`(`PUT /admin/posts/{id}`)
  - `lib/api/admin-categories.ts`(새 파일): `getCategories()`(`GET /categories`, 공개 API를 브라우저에서 부른다). 관리자 카테고리 화면(#78)도 이 파일에 생성·수정·삭제를 더한다. 서버 컴포넌트용 `posts.ts`는 `'use cache'`(60초)라 쓰지 않는다: 방금 만든 카테고리가 폼에 늦게 보인다.

**불러오기** (폼 대신 보이는 문구, 제목은 항상 보인다. 두 요청의 실패가 겹치면 표의 위쪽 행이 우선한다: `401` > 글 `404` > 그 외)
| 상태 | 화면 |
|---|---|
| 불러오는 중 | "불러오는 중..." |
| `401` (글·카테고리 중 하나라도) | `client.ts`가 로그인 화면으로 이동시키므로 "불러오는 중..."을 유지한다 ('관리자 글 목록'과 같다) |
| 글 `404` (`POST_NOT_FOUND`) | "글을 찾을 수 없습니다."와 "글 목록으로"(`/admin/posts`) 링크 (카테고리 요청이 실패해도 같다) |
| 그 외 `ApiError`(400, 403, 429, 5xx)·네트워크 오류 (글·카테고리 중 하나라도) | "글을 불러오지 못했습니다. 새로고침해 주세요." |

**입력 항목** (순서대로)
| 항목 | 입력 | 수정 폼의 초기값 |
|---|---|---|
| 제목 | `input` | `title` |
| slug | `input`, 아래 안내 "영문 소문자·숫자·하이픈만 쓸 수 있습니다. 공개 주소 /posts/{slug}가 됩니다." | `slug` |
| 카테고리 | `select`, 첫 항목 "카테고리 없음"(값 `""`), 이어서 목록 API 순서대로 "{name} ({slug})" (`name`은 중복될 수 있어 slug를 함께 보인다, 6장) | `categoryId`, `null`이거나 카테고리 목록에 없으면 `""` |
| 태그 | `input`, 아래 안내 "쉼표(,)로 구분해 10개까지 입력합니다. 문자·숫자·하이픈만 쓸 수 있고 영문은 소문자로 저장됩니다." | `tags.join(", ")`, `[]`이면 `""` |
| 상태 | 라디오 "작성 중"(`DRAFT`)·"발행됨"(`PUBLISHED`), 새 글 기본값 `DRAFT` | `status` |
| 요약 | `textarea` | `summary`, `null`이면 `""` |
| 썸네일 URL | `input` | `thumbnailUrl`, `null`이면 `""` |
| 본문 | "작성"·"미리보기" 전환 버튼과 `textarea` | `content` |
- 불러온 글의 `categoryId`가 카테고리 목록에 없으면(두 요청 사이에 카테고리가 삭제됨) 위 초기값 규칙으로 `""`이 되어 선택이 "카테고리 없음"으로 보이고, 그대로 저장하면 카테고리 없는 글이 된다. 목록에 없는 id를 그대로 두지 않는 이유: 맞는 `option`이 없는 `select`는 빈 칸으로 보이고, 저장하면 그 id가 전송되어 `404 CATEGORY_NOT_FOUND`가 된다. 카테고리 삭제가 글의 카테고리를 `NULL`로 만드므로(6장) 결과가 같아 따로 다루지 않는다.
- **태그는 쉼표로 구분한 한 줄 `input`으로 받는다** (폼 값은 문자열 하나). 칩 입력(Enter로 추가, ×로 삭제)을 쓰지 않는 이유: 태그 배열 상태·삭제 버튼·키 처리가 더 필요하고, 한글 IME 조합 중 Enter가 두 번 처리되는 문제를 따로 막아야 한다. 관리자 1명이 글당 몇 개 입력하는 용도라 한 줄 입력으로 충분하다.
  - 자동완성은 두지 않는다. 공개 태그 목록(8-1 `GET /tags`)은 DRAFT에만 달린 태그를 빼므로 관리자용 후보 목록으로 맞지 않고, 관리자용 목록 API를 더할 만큼 태그가 많지 않다.
- 폼은 항상 `tags`를 보낸다 (입력이 비면 `[]`). 수정은 전체 교체라(8-4 `PUT`) 칸을 비우고 저장하면 그 글의 태그 연결이 모두 지워진다.
- 폼 아래 "저장" 버튼 하나. 저장할 상태는 위 라디오가 정한다.

**`postSchema`** (`schemas/post.ts`, Zod) — 서버 규칙(8-4 `PostRequest`)과 같고, 메시지도 서버의 검증 메시지와 같다
| 필드 | 규칙 | 메시지 |
|---|---|---|
| title | 앞뒤 공백 제거(`.trim()`) 후 1~200자, 제어 문자 금지(`/^\P{Cc}+$/u`) | "제목을 입력하세요", "제목은 200자 이하여야 합니다", "제목에 제어 문자를 쓸 수 없습니다" |
| slug | 빈 값 거절, `MAX_SLUG_LENGTH`(200) 이하, `SLUG_PATTERN`에 맞음. 공백을 지우지 않는다 (서버도 지우지 않아 공백이 있으면 형식 위반이다) | "slug를 입력하세요", "slug는 200자 이하여야 합니다", "slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다" |
| summary | 앞뒤 공백 제거 후 500자 이하, NUL 금지. 빈 값 허용 | "요약은 500자 이하여야 합니다", "요약에 NUL 문자를 쓸 수 없습니다" |
| content | 공백만 있는 값 거절, 100,000자 이하, NUL 금지. 앞뒤 공백은 그대로 둔다 | "본문을 입력하세요", "본문은 100,000자 이하여야 합니다", "본문에 NUL 문자를 쓸 수 없습니다" |
| thumbnailUrl | 앞뒤 공백 제거 후 빈 값 허용, 500자 이하, 값이 있으면 `/^https?:\/\/[^\s\p{Cc}]+$/u` | "썸네일 URL은 500자 이하여야 합니다", "썸네일 URL은 공백 없는 http(s) 주소여야 합니다" |
| categoryId | 문자열(`""` 또는 목록의 id) | - |
| tags | 문자열. `parseTags`로 나눈 목록을 `validateTags`로 검사해, 위반이 있으면 그 메시지 하나를 `tags` 필드 오류로 (`superRefine`) | 8-4 '태그 처리'의 `reason` 메시지 표와 같다 |
| status | `DRAFT`·`PUBLISHED` | - |
- slug 규칙은 `lib/slug.ts`가 `SLUG_PATTERN`·`MAX_SLUG_LENGTH`를 export해 `isValidSlug`와 스키마가 같은 상수를 쓴다. 스키마가 `isValidSlug` 하나로 검사하지 않는 이유: 필드 오류를 빈 값·길이·형식으로 나눠 서버와 같은 문구로 보인다.
- 태그 규칙은 `lib/tags.ts`의 순수 함수 두 개가 서버(8-4 '태그 처리')와 같은 순서로 처리한다.
  - `parseTags(value: string): string[]`: 쉼표로 나눠 각 조각을 `trim()`·`toLowerCase()`하고, 빈 조각을 버린 뒤 처음 나온 순서대로 중복을 지운다 (`"Spring, spring,, jwt,"` → `["spring", "jwt"]`). `toLowerCase()`는 로케일과 무관해 서버의 `toLowerCase(Locale.ROOT)`와 같다.
  - 빈 조각을 오류로 보지 않고 버리는 이유: 끝의 쉼표나 `,,`는 입력 실수라 "빈 태그는 쓸 수 없습니다"로 막을 값이 아니다. 그래서 폼에서는 항목 필수 위반이 나오지 않고, 서버의 그 규칙은 직접 요청만 막는다.
  - `validateTags(tags: string[]): string | null`: 개수(`MAX_TAGS` 10) 초과를 먼저 보고, 이어서 앞 항목부터 한 항목씩 길이(`MAX_TAG_LENGTH` 50) 초과 → 패턴(`TAG_PATTERN`, 6장 태그 규칙에 `u` 플래그) 위반을 본 뒤 다음 항목으로 간다. 처음 걸린 규칙의 메시지(8-4 메시지 표)를 내고, 없으면 `null`. 그래서 앞 항목의 패턴 위반이 뒤 항목의 길이 초과보다 먼저다 (`["a_b", "x"×51]` → 패턴 메시지). 서버는 위반을 모두 `errors`로 내지만 폼은 한 칸이라 첫 위반 하나만 보인다.
  - 요청 본문의 `tags`는 `parseTags(값)`이다 (서버가 다시 정규화해도 결과가 같다).
- 길이는 JS 문자열 길이(UTF-16)로 세어 서버(Java 문자 수)와 같다. 공백 판정(`trim()`·`\s`)은 JS가 NBSP 등 유니코드 공백까지 포함해 Java(`strip()`·`\s`)와 조금 다를 수 있다 (`loginSchema`와 같다). 서버 검증이 최종이다.
- 요청 본문: 스키마를 통과한 값(앞뒤 공백을 지운 `title`·`summary`·`thumbnailUrl`)을 보낸다. `summary`·`thumbnailUrl`의 `""`는 그대로 보내 서버가 `null`로 바꾼다 (8-4). `categoryId`는 `""`면 넣지 않고(수정에서는 카테고리가 지워진다, 8-4 `PUT`), 그 외는 `Number`로 바꾼다.
- `postSchema`는 `loginSchema`와 같은 이유(라이브러리 규칙을 그대로 선언)로 단위 테스트하지 않고 아래 화면 확인 2번으로 표의 `title`부터 `thumbnailUrl`까지의 규칙을 모두 본다. `tags`의 경계는 `parseTags`·`validateTags` 단위 테스트('프론트엔드 테스트')로 보고, 화면 확인 11번은 스키마 연결과 표시만 본다. `categoryId`·`status`는 `select`·라디오가 값을 정해 따로 보지 않는다.

**미리보기** (`MarkdownPreview`, 클라이언트 컴포넌트)
- `MarkdownRenderer`는 Shiki를 쓰는 async 서버 컴포넌트라 폼에서 쓸 수 없다. 미리보기는 브라우저에서 react-markdown의 동기 `Markdown`으로 렌더링하고 **코드 하이라이팅은 하지 않는다** (코드 블록은 글자만).
  - 서버 렌더링 요청(Server Action·API)을 두지 않는 이유: 미리보기마다 서버 왕복과 Shiki 실행이 생기는데 결과는 하이라이팅 색만 다르다. 클라이언트 Shiki는 하이라이터와 문법 파일이 관리자 번들에 들어간다. 관리자 1명이 내용을 확인하는 용도라 색은 필요 없다고 본다.
- 플러그인은 `remarkGfm`만 쓴다 (표·체크리스트·취소선을 공개 화면과 같게 보인다). `rehype-slug`는 제목 `id`만 만들어 보이는 결과가 같으므로 넣지 않는다.
- 보안은 `MarkdownRenderer`와 같다: `rehype-raw`, `skipHtml` 변경, `urlTransform` 재정의를 하지 않는다 (10-4). 스타일도 같은 `<div className="prose dark:prose-invert max-w-none">`로 감싼다.
- "미리보기"를 누르면 그 시점의 본문으로 렌더링하고, "작성"을 누르면 `textarea`로 돌아간다. 미리보기 중에는 `textarea`를 `hidden`으로 숨기고 지우지 않는다 (입력값과 검증 대상 유지). 입력마다 렌더링하는 나란히 보기는 하지 않는다 (100,000자 본문을 키 입력마다 다시 파싱한다). 본문이 공백뿐이면 "미리 볼 내용이 없습니다."
- 현재 선택된 버튼은 `aria-pressed="true"`. 두 버튼은 `type="button"`이라 폼을 제출하지 않는다.

**저장**
- 제출하면 Zod 검증 → (수정이고, 불러온 글이 `PUBLISHED`이고, slug를 바꿨으면) `window.confirm("slug를 바꾸면 기존 주소 /posts/{불러온 slug}는 더 이상 열리지 않습니다. 저장할까요?")`, 취소하면 요청하지 않는다 (8-4 `PUT` slug 변경, 리다이렉트 없음) → 새 글은 `createPost`, 수정은 `updatePost`.
- 요청 중에는 "저장" 버튼을 막는다.
- 제출할 때 이전 폼 오류 문구를 지운다. 필드 오류는 `setError`로 각 필드 아래에, 폼 오류는 "저장" 버튼 위에 `role="alert"`로 보인다. 입력값은 그대로 둔다.
- 포커스: Zod 오류는 React Hook Form 기본값(`shouldFocusError`)대로 첫 오류 필드로 간다. 서버 응답의 필드 오류는 포커스를 옮기지 않는다.
- 미리보기 중 본문 오류(Zod, 서버 `400`의 `content`)가 나면 "작성"으로 전환해 `textarea`와 그 아래 문구가 보이게 한다. 첫 오류 필드가 본문인 Zod 오류는 전환 렌더링이 끝난 뒤(`useEffect`) `setFocus("content")`를 부른다: 숨은 요소는 포커스를 받지 못해 `shouldFocusError`만으로는 본문에 포커스가 가지 않는다.
  | 결과 | 화면 |
  |---|---|
  | `201`(작성)·`200`(수정) | `router.push("/admin/posts")` (목록의 공개 반영 안내가 보인다) |
  | `400` `INVALID_INPUT`이고 `errors`에 폼 필드(`title`·`slug`·`summary`·`content`·`thumbnailUrl`·`categoryId`·`status`·`tags`)가 있음 | 그 필드 아래에 `reason` (같은 필드가 여러 개면 첫 항목, 폼 필드가 아닌 항목은 버린다). 항목 위반의 `tags[i]`는 `tags`로 본다: `i`가 정규화 후 위치라(8-4) 입력의 몇 번째인지 가리킬 수 없고, 태그 칸이 하나다 |
  | `409` `DUPLICATE_SLUG` | slug 아래에 `detail`("이미 사용 중인 slug입니다.") |
  | `404` `CATEGORY_NOT_FOUND` (선택한 카테고리가 그 사이 삭제됨) | 카테고리 아래에 `detail`("카테고리를 찾을 수 없습니다.") |
  | `404` `POST_NOT_FOUND` (수정 중 다른 탭에서 삭제됨) | 폼 오류 "글이 삭제되어 저장하지 못했습니다." |
  | `401` | `client.ts`가 로그인 화면으로 이동시킨다 (문구 없음, 아래 이탈 경고가 뜬다) |
  | 그 외 `ApiError`(폼 필드가 없는 `400`, 403, 429, 5xx, csrf 호출 실패)·네트워크 오류 | 폼 오류 "저장하지 못했습니다. 잠시 후 다시 시도해 주세요." |
  - `400`은 Zod가 같은 규칙으로 먼저 막으므로 공백 판정 차이(위)나 규칙 불일치 버그일 때만 나온다. `409`·`CATEGORY_NOT_FOUND`의 문구는 9장대로 `detail`을 쓴다. `POST_NOT_FOUND`만 고정 문구인 이유: `detail`("글을 찾을 수 없습니다.")로는 저장하지 못했다는 결과가 드러나지 않는다.

**이탈 경고**
- 폼이 기준값에서 바뀐 동안(`formState.isDirty`) `window`에 `beforeunload` 리스너를 두어, 새로고침·탭 닫기·주소 직접 입력·`window.location.assign`(로그아웃, `client.ts`의 401 이동) 때 브라우저 기본 확인 창을 띄운다. 문구는 브라우저가 정한다 (임의 문구는 표시되지 않는다).
  - 401 이동에서 머무르기를 고르면 화면에 남아 입력을 복사해 둘 수 있다. 다시 저장해도 401이면 같은 확인 창이 뜬다.
- **앱 안의 클라이언트 이동(헤더 "글"·"카테고리" 링크, 브라우저 뒤로 가기)은 경고하지 않는다.** App Router에 클라이언트 이동을 막는 API가 없어, 막으려면 문서의 링크 클릭과 `popstate`를 직접 가로채야 한다. 관리자 1명이 쓰는 화면이라 그 코드를 유지할 만큼 얻는 것이 없다고 본다.
- 저장에 성공하면 `router.push`로 이동해 폼이 사라지므로 리스너도 정리된다 (경고 없음).

**화면 확인** ('관리자 글 목록'의 화면 확인과 같은 환경. 완료 조건의 확인 수단이다)
1. 목록의 "새 글" → `/admin/posts/new`. 카테고리 선택에 "카테고리 없음"과 "{name} ({slug})" 항목, 상태 기본값 "작성 중". 값을 채워 저장 → `POST` `201` 뒤 `/admin/posts`, 목록 맨 위에 그 글
2. `postSchema` 규칙 (요청 본문은 `Network`의 `POST` 페이로드로 본다)
   - 필수·형식: 빈 폼 저장 → 제목·slug·본문 아래 필수 문구, `Network`에 `POST` 없음. 공백만 있는 제목·본문, `My Slug`·` my-slug`(앞 공백), 201자 slug, `ftp://a`·`https://a b` 썸네일 → 각 문구
   - 길이 경계: 201자 제목, 501자 요약, 100,001자 본문, 501자 썸네일 URL → 각 길이 문구 (200·500·100,000·500자는 통과). 200자 slug, 앞뒤 공백을 붙인 200자 제목·500자 요약·500자 썸네일 URL도 통과하고, 요청 본문에는 공백을 지운 값이 간다
   - 제어 문자·NUL: DevTools 콘솔에서 값을 넣고 저장 → 각 문구. 대상은 제목에 `a\tb`, 썸네일 URL에 `https://a\u0001b`, 요약·본문에 `\u0000` (`Tab` 키는 포커스를 옮겨 입력되지 않는다). 제목·썸네일 URL은 앞뒤 공백을 지우므로 제어 문자를 가운데에 넣고, 썸네일 URL은 `\s`에 들지 않는 제어 문자로 `\p{Cc}` 검사를 본다. 넣는 방법: `Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(el, 값)`(`textarea`는 `HTMLTextAreaElement`) 뒤 `el.dispatchEvent(new Event("input", { bubbles: true }))`. React는 루트에서 버블 단계로 이벤트를 받으므로 `bubbles: true`가 없으면 폼 값이 바뀌지 않는다
   - 공백 처리: 앞뒤 공백이 있는 제목은 지운 값으로 저장된다. 앞뒤에 공백·줄바꿈이 있는 본문은 요청 본문에 그대로 간다. 공백만 있는 요약·썸네일 URL은 오류 없이 저장되고 요청 본문은 `""`. `POST` 응답에는 두 필드가 없으므로, 저장한 글의 수정 화면을 열어 `GET /admin/posts/{id}` 응답의 `summary`·`thumbnailUrl`이 `null`이고 두 칸이 비어 있는지 본다
3. 이미 있는 slug로 저장 → slug 아래 "이미 사용 중인 slug입니다.", 입력 유지. 폼을 연 뒤 그 카테고리를 `DELETE /api/v1/admin/categories/{id}`로 지우고 그 카테고리로 저장 → 카테고리 아래 "카테고리를 찾을 수 없습니다."
4. 목록의 제목 링크 → `/admin/posts/{id}/edit`에 값이 채워진다 (카테고리 없는 글은 "카테고리 없음", `null`인 요약·썸네일은 빈 칸). 수정 후 저장 → `PUT` `200` 뒤 목록의 수정일 반영. 카테고리를 "카테고리 없음"으로 저장 → 목록의 카테고리 "-". 카테고리 있는 글의 수정 화면에서 DevTools Local Overrides로 `GET /categories` 응답에서 그 카테고리를 빼고 새로고침 → "카테고리 없음"으로 보이고, 그대로 저장 → `PUT` 본문에 `categoryId` 없음, `200`
5. `PUBLISHED` 글의 slug를 바꿔 저장 → confirm에 불러온 slug, 취소 → 요청 없음, 확인 → 저장. `DRAFT` 글의 slug 변경은 confirm 없음
6. `/admin/posts/abc/edit`, `/0/edit`, `/01/edit`, `/9007199254740992/edit` → "잘못된 주소입니다.", `Network`에 `admin/posts/` 요청 없음. 없는 id(`/999999/edit`) → "글을 찾을 수 없습니다."와 "글 목록으로". DevTools로 `categories` 요청을 막고 새로고침 → "글을 불러오지 못했습니다. 새로고침해 주세요." `categories` 요청을 막은 채 없는 id → "글을 찾을 수 없습니다."
7. 수정 화면을 연 뒤 그 글을 `DELETE`로 지우고 저장 → "글이 삭제되어 저장하지 못했습니다." DevTools로 `admin/posts*` 요청을 막고 저장 → "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", 입력 유지. 스로틀링 시 요청 중 "저장" 버튼이 막힌다
8. 표·체크리스트·코드 블록·`<script>`·`<img onerror>`·`[x](javascript:alert(1))`가 든 본문 → "미리보기"에서 표·체크리스트가 보이고, raw HTML은 글자로 보이며 스크립트가 실행되지 않고, `javascript:` 링크의 `href`가 비어 있다 (DOM으로 확인). "작성"으로 돌아오면 입력이 남아 있다. 공백뿐인 본문 → "미리 볼 내용이 없습니다." 다른 항목을 채우고 본문을 비운 채 "미리보기" 상태에서 저장 → "작성"으로 바뀌고 본문 아래 "본문을 입력하세요", 포커스가 본문 `textarea`
9. 값을 바꾼 뒤 새로고침 → 브라우저 확인 창, 바꾸지 않았으면 확인 창 없음. 수정 화면에서 값을 바꾼 뒤 `access_token`·`refresh_token`을 지우고 저장 → 확인 창, 떠나기 → `/admin/login?next=%2Fadmin%2Fposts%2F{id}%2Fedit`
10. `.env.local` 없이 `npm run build`와 `npx next typegen` 성공 ('관리자 인증' 8번과 같다)
11. 태그: 새 글에 ` Spring, jwt, spring,, 한글-태그,` 입력 후 저장 → `POST` 본문 `tags`가 `["spring","jwt","한글-태그"]`, 수정 화면에 `jwt, spring, 한글-태그`(8-1 `tags` 순서인 이름 오름차순이라 입력 순서는 보존되지 않는다). 수정 화면에서 태그를 지우고 저장 → `PUT` 본문 `tags: []`, 다시 열면 빈 칸. 태그 11개, 51자 태그, `a_b` → 태그 아래 각 문구, `Network`에 요청 없음. 태그만 바꾸면 이탈 경고가 뜬다(`isDirty`). Local Overrides로 `PUT` 응답을 `400` `INVALID_INPUT`, `errors: [{"field":"tags[0]","reason":"태그는 50자 이하여야 합니다"}]`로 바꾸고 저장 → 태그 아래 그 문구

### 관리자 카테고리 (`/admin/categories`)
**구성**
- `categories/page.tsx`(서버 컴포넌트): 제목 "카테고리"와 `AdminCategoryManager`(클라이언트, `components/admin/`)만 둔다. 읽을 쿼리·경로 값이 없어 `Suspense`를 두지 않는다.
- 한 화면에 폼과 목록을 함께 둔다: 위에 `CategoryForm`(생성·수정 공용, 클라이언트), 아래에 목록 표. 입력이 두 칸뿐이고 단건 조회 API가 없어(8-4) 글처럼 작성·수정 경로를 나누지 않는다. 행마다 입력칸을 펼치는 인라인 편집도 하지 않는다: 폼이 하나여야 검증·오류 표시가 한 곳에 모인다.
- `AdminCategoryManager`가 목록과 "수정 중인 카테고리"(`null`이면 생성 모드)를 상태로 갖고 폼에 넘긴다. 목록은 마운트 때와 저장·삭제 뒤 `getCategories()`로 다시 불러온다. 늦게 온 이전 응답은 버리고, 데이터 패칭 라이브러리는 쓰지 않는다 ('관리자 글 목록'과 같다).
- API: `lib/api/admin-categories.ts`에 더한다 (`client.ts` 사용, 타입은 `api.d.ts`의 `CategoryRequest`·`AdminCategoryResponse`): `createCategory(body)`(`POST /admin/categories`), `updateCategory(id, body)`(`PUT /admin/categories/{id}`), `deleteCategory(id)`(`DELETE /admin/categories/{id}`). 저장 응답은 쓰지 않고 목록을 다시 불러온다 (`postCount`가 없다, 8-4).

**화면**
- 제목 아래 안내 한 줄: "변경한 내용은 1분이 지난 뒤 공개 페이지를 열면 갱신되며, 한 번 더 새로고침해야 보일 수 있습니다." ('관리자 글 목록'과 같은 문구. 공개 글 화면의 카테고리 이름은 `posts.ts`·`taxonomy.ts` 캐시를 거친다, '렌더링 전략'. 즉시 반영은 하지 않는다)
- 표 열: 이름, slug, 발행 글 수(`postCount`), 수정 버튼, 삭제 버튼. 순서는 API 정렬(`name` 오름차순, 8-1) 그대로다. `name`은 중복될 수 있어 slug 열로 구분한다 (6장). 열 이름을 "발행 글 수"로 하는 이유: `postCount`는 PUBLISHED만 세어(8-1) 작성 중 글이 빠진다.
- 목록 상태별 표시 (제목·안내·폼은 항상 보인다)
  | 상태 | 화면 |
  |---|---|
  | 불러오는 중 (처음, 저장·삭제 뒤 다시 불러올 때) | 표 자리에 "불러오는 중..." (이전 표는 지운다) |
  | 빈 배열 | "카테고리가 없습니다." |
  | `401` | `client.ts`가 로그인 화면으로 이동시키므로 "불러오는 중..."을 유지한다 |
  | 그 외 `ApiError`(403, 429, 5xx)·네트워크 오류 | "카테고리 목록을 불러오지 못했습니다. 새로고침해 주세요." |

**폼** (`CategoryForm`, react-hook-form + `zodResolver(categorySchema)`)
- 항목: 이름(`input`), slug(`input`, 아래 안내 "영문 소문자·숫자·하이픈만 쓸 수 있습니다.").
- 생성 모드: 제목 "새 카테고리", 빈 값, 버튼 "추가".
- 행의 "수정"을 누르면 수정 모드: 제목 "카테고리 수정", 그 행의 `name`·`slug`로 `reset`하고 이전 폼 오류를 지운다. 버튼은 "저장"과 "취소"(생성 모드로 돌아가 빈 값으로 `reset`). 이름 입력에 포커스(`setFocus("name")`)를 옮긴다: 폼이 표 위에 있어 키보드 사용자가 찾아가지 않아도 되게. 수정 중 다른 행의 "수정"을 누르면 확인 없이 그 행으로 바꾼다 (잃는 입력이 두 칸뿐이다).
- 이탈 경고(`beforeunload`)는 두지 않는다. 같은 이유로 입력이 두 칸뿐이다.
- slug를 바꾸면 저장 전에 확인 창을 띄운다 (아래 '저장'). 카테고리 페이지 `/categories/{slug}`는 글이 없는 카테고리에도 있어('카테고리·태그 페이지') 글과 달리 상태 조건이 없고, 리다이렉트는 두지 않는다 (글 slug와 같다).

**`categorySchema`** (`schemas/category.ts`, Zod) — 서버 규칙(8-4 `CategoryRequest`)과 같고, 메시지도 서버의 검증 메시지와 같다
| 필드 | 규칙 | 메시지 |
|---|---|---|
| name | 앞뒤 공백 제거(`.trim()`) 후 1~50자, 제어 문자 금지(`/^\P{Cc}+$/u`) | "카테고리 이름을 입력하세요", "카테고리 이름은 50자 이하여야 합니다", "카테고리 이름에 제어 문자를 쓸 수 없습니다" |
| slug | 빈 값 거절, 100자 이하, `SLUG_PATTERN`에 맞음. 공백을 지우지 않는다 (서버도 지우지 않는다) | "slug를 입력하세요", "slug는 100자 이하여야 합니다", "slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다" |
- 패턴은 `lib/slug.ts`의 `SLUG_PATTERN`을 쓰고, 길이 상한은 `category.slug` 컬럼(6장)에 맞춰 100이다 (`MAX_SLUG_LENGTH` 200은 글 slug다). 길이(UTF-16)·공백 판정의 서버와의 차이는 `postSchema`와 같다.
- 요청 본문은 스키마를 통과한 값(앞뒤 공백을 지운 `name`)을 보낸다.
- `postSchema`와 같은 이유로 단위 테스트하지 않고 아래 화면 확인 2번으로 표의 규칙을 모두 본다.

**저장**
- 제출하면 Zod 검증 → (수정 모드이고, 스키마를 통과한 slug가 수정 중인 카테고리의 slug와 다르면) `window.confirm("slug를 바꾸면 기존 주소 /categories/{수정 중인 카테고리의 slug}는 더 이상 열리지 않습니다. 저장할까요?")`, 취소하면 요청하지 않고 수정 모드와 입력을 그대로 둔다 → 생성 모드는 `createCategory`, 수정 모드는 `updateCategory`. 요청 중에는 "추가"·"저장"·"취소" 버튼과 표의 수정·삭제 버튼을 막는다 (요청 중 모드가 바뀌거나 저장 대상이 지워지지 않게).
- 제출할 때 이전 폼 오류를 지운다. 필드 오류는 각 필드 아래, 폼 오류는 버튼 위에 `role="alert"`로 보이고 입력값은 그대로 둔다. Zod 오류의 포커스는 React Hook Form 기본값을 따르고, 서버 응답의 필드 오류는 포커스를 옮기지 않는다 ('관리자 글 작성·수정'과 같다).
  | 결과 | 화면 |
  |---|---|
  | `201`(생성)·`200`(수정) | 생성 모드로 돌아가 빈 값으로 `reset`, 목록을 다시 불러온다 |
  | `400` `INVALID_INPUT`이고 `errors`에 `name`·`slug`가 있음 | 그 필드 아래에 `reason` (같은 필드가 여러 개면 첫 항목, 그 밖의 항목은 버린다) |
  | `409` `DUPLICATE_SLUG` | slug 아래에 `detail`("이미 사용 중인 slug입니다.") |
  | `404` `CATEGORY_NOT_FOUND` (수정 중 다른 탭에서 삭제됨) | 폼 오류 "카테고리가 삭제되어 저장하지 못했습니다.", 목록을 다시 불러온다. 수정 모드와 입력은 그대로 둔다 (내용을 복사해 둘 수 있게. "취소"로 생성 모드로 나간다) |
  | `401` | `client.ts`가 로그인 화면으로 이동시킨다 (문구 없음) |
  | 그 외 `ApiError`(폼 필드가 없는 `400`, 403, 429, 5xx, csrf 호출 실패)·네트워크 오류 | 폼 오류 "저장하지 못했습니다. 잠시 후 다시 시도해 주세요." |
  - `CATEGORY_NOT_FOUND`를 `detail` 대신 고정 문구로 보이는 이유는 '관리자 글 작성·수정'의 `POST_NOT_FOUND`와 같다.

**삭제**
- 버튼을 누르면 `window.confirm("\"{name}\" 카테고리를 삭제할까요? 이 카테고리의 글(작성 중인 글 포함)은 삭제되지 않고 카테고리 없음이 됩니다.")`로 묻고, 취소하면 요청하지 않는다.
  - 글이 있을 때만 경고하지 않고 항상 같은 문구를 쓴다: `postCount`는 PUBLISHED만 세어 작성 중 글이 있어도 0일 수 있고, 모든 글을 세려면 관리자용 목록 API가 필요한데 두지 않기로 했다(8-1). 삭제가 글을 지우지 않으므로(8-4) 결과를 문구로 알리는 것으로 충분하다고 본다.
- 요청 중에는 표의 수정·삭제 버튼과 폼의 버튼을 막는다.
  | 결과 | 화면 |
  |---|---|
  | `204` | 목록을 다시 불러온다. 지운 카테고리를 수정 중이었으면 생성 모드로 돌아가 빈 값으로 `reset` |
  | `404` (`CATEGORY_NOT_FOUND`, 이미 삭제됨) | 바라던 상태라 `204`와 같다 (문구 없음) |
  | `401` | `client.ts`가 로그인 화면으로 이동시킨다 (문구 없음) |
  | 그 외 `ApiError`(403, 429, 5xx, csrf 호출 실패)·네트워크 오류 | `window.alert("삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.")`, 목록과 폼은 그대로 둔다 |
- 다시 불러오기(저장·삭제 뒤)의 표시와 실패는 위 목록 상태별 표를 따른다.

**화면 확인** ('관리자 글 목록'의 화면 확인과 같은 환경. local 프로필은 시작할 때 글이 없으면 시드 카테고리·글을 넣으므로, 1번 전에 시드 카테고리를 모두 `DELETE /api/v1/admin/categories/{id}`로 지우고 1번을 볼 때까지 백엔드를 재시작하지 않는다. 완료 조건의 확인 수단이다)
1. 헤더 "카테고리" → `/admin/categories`. 제목·안내 문구·"새 카테고리" 폼과 "카테고리가 없습니다."
2. `categorySchema` 규칙: 빈 폼 "추가" → 이름·slug 필수 문구, `Network`에 `POST` 없음. 공백만 있는 이름, 51자 이름, `My Slug`·` my-slug`(앞 공백)·101자 slug → 각 문구. 50자 이름, 앞뒤 공백을 붙인 50자 이름, 100자 slug는 통과하고 요청 본문의 `name`은 공백을 지운 값. 이름 가운데의 `a\tb`('관리자 글 작성·수정' 화면 확인 2번의 방법으로 넣는다) → 제어 문자 문구
3. 추가 → `POST` `201`, 폼이 비고 목록에 그 카테고리(발행 글 수 0). 이름 순으로 보이고, 같은 이름 두 개는 slug로 구분된다. 이미 있는 slug로 추가 → slug 아래 "이미 사용 중인 slug입니다.", 입력 유지
4. "수정" → 제목 "카테고리 수정", 값이 채워지고 이름에 포커스. 이름만 바꿔 저장 → confirm 없이 `PUT` `200`, 생성 모드로 돌아오고 목록에 반영. slug를 바꿔 저장 → confirm에 수정 전 slug, 취소 → 요청 없음·수정 모드와 입력 유지, 확인 → `PUT` `200`. 다른 카테고리의 slug로 저장 → confirm 확인 뒤 "이미 사용 중인 slug입니다." "취소" → 생성 모드, 빈 값
5. 수정 중인 카테고리를 다른 탭에서 지우고 저장 → "카테고리가 삭제되어 저장하지 못했습니다.", 목록에서 사라지고 입력 유지. DevTools로 `admin/categories*`를 막고 저장 → "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", 입력 유지. 스로틀링 시 요청 중 폼과 표의 버튼이 막힌다. `access_token`·`refresh_token`을 지우고 저장 → `/admin/login?next=%2Fadmin%2Fcategories`
6. 발행 글과 작성 중 글이 있는 카테고리 삭제 → confirm 문구에 이름, 취소 → 요청 없음, 확인 → `DELETE` `204`, 목록에서 사라지고 그 글들의 수정 화면이 "카테고리 없음". 수정 중인 카테고리 삭제 → 생성 모드로 돌아감. 다른 탭에서 먼저 지운 카테고리 삭제 → `404`, 문구 없이 갱신. DevTools로 `admin/categories/*`를 막고 삭제 → alert. 스로틀링 시 `DELETE` 중 버튼이 막히고, 다시 불러오는 동안 "불러오는 중..." `access_token`·`refresh_token`을 지우고 삭제 → confirm 확인 → `/admin/login?next=%2Fadmin%2Fcategories`
7. DevTools로 `categories`(목록 조회)를 막고 새로고침 → "카테고리 목록을 불러오지 못했습니다. 새로고침해 주세요.", 폼은 보인다. `api/v1/categories`(공개 목록 조회)만 막고(`api/v1/admin/categories`는 이 패턴에 들지 않아 막히지 않는다) 추가 → `POST` `201`, 삭제 → `DELETE` `204` 뒤 각각 같은 문구가 나오고 폼은 보인다
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
- 구성: `page.tsx`는 `Suspense`(fallback은 짧은 로딩 문구)만 두고, 그 안의 비동기 컴포넌트가 `searchParams`를 읽어 `getPosts(page - 1)`(`lib/api/posts.ts`, `'use cache'` + `cacheLife({ revalidate: 60 })`)와 태그 목록용 `getPublicTags()`(아래 '태그 목록 노출')를 함께(`Promise.all`) 호출한다. 캐시는 인자(`page`)별로 나뉜다. `getPosts`의 필터 인자는 '카테고리·태그 페이지'.
- `PostCard` 표시 항목: 제목(`/posts/{slug}` 링크), 요약(있을 때), 카테고리 이름(있을 때, `categoryHref(slug, 1)` 링크), 발행일(`formatDate`), 태그(`tags`가 `[]`가 아닐 때, 카테고리·발행일 줄 아래에 응답 순서대로 `TagBadge`, 개수 없음). `TagBadge`와 주소 함수는 '카테고리·태그 페이지'.
  - 썸네일은 M1에서 표시하지 않는다 (업로드는 M6이고, 외부 URL은 `next/image` 허용 도메인 설정이 필요하다).
- 페이지 이동(`Pagination`): "이전"·"다음" 링크와 현재 위치(`2 / 3`)만 둔다. 링크 주소는 `pageHref(page)` prop으로 받는다 (이 페이지는 위의 `/`·`/?page=N` 규칙, 관리자 목록은 '관리자 글 목록'). "이전"은 `page > 1`, "다음"은 `hasNext`일 때 보이고, 전체가 한 페이지 이내면 영역을 숨긴다. 페이지 번호 목록은 글이 많아지면 검토한다.
- 글이 하나도 없으면(첫 페이지 `content`가 빔) "아직 작성된 글이 없습니다." 문구를 보여 준다.
- **태그 목록 노출**: 글 목록 위에 `<nav aria-label="태그">`로 `getPublicTags()` 전체를 응답 순서(글 많은 순, 8-1)대로 `TagBadge`(개수 포함)로 보인다. 모든 페이지에 같게 보이고, 태그가 없으면 `nav`를 그리지 않는다. `page`가 잘못되어 `notFound()`이면 그리지 않는다. 태그 목록 조회가 실패하면 글 목록과 같이 오류 화면이다 ('에러 처리').
  - `/tags` 목록 페이지나 헤더 메뉴는 두지 않는다. 태그가 수십 개(8-1)라 한 묶음이면 충분하고, 헤더에 두면 모든 공개 페이지(상세·검색)가 태그 API를 부른다.
  - 카테고리 목록 메뉴는 두지 않는다. 카테고리 진입은 `PostCard`·상세의 카테고리 링크로 한다. 필요해지면 다시 검토한다.
- 레이아웃: `app/page.tsx`를 `app/(public)/page.tsx`로 옮기고, `(public)/layout.tsx`에 블로그 이름(`/` 링크) 헤더를 둔다. 푸터는 'RSS 피드·푸터'.

### 글 상세 페이지 (`/posts/{slug}`)
- 구성: 목록 페이지와 같다. `page.tsx`는 `Suspense`만 두고, 그 안의 비동기 컴포넌트가 `params`를 읽어 `getPost(slug)`(`lib/api/posts.ts`, `'use cache'` + `cacheLife({ revalidate: 60 })`)를 호출한다.
- `getPost`를 호출하기 전에 `lib/slug.ts`의 순수 함수 `isValidSlug`로 6장 slug 규칙(`^[a-z0-9]+(?:-[a-z0-9]+)*$`, 200자 이하)을 검사하고, 맞지 않으면 `notFound()`.
  - 규칙에 맞는 값은 영문 소문자·숫자·하이픈뿐이라 그대로 API 경로에 넣는다. 규칙 밖 값을 그대로(또는 `encodeURIComponent`로) 넣으면 `.`이 든 값은 URL 정규화로 다른 API 경로가 될 수 있고(`encodeURIComponent`는 `.`을 인코딩하지 않는다), 인코딩된 슬래시(`%2F`)는 서버가 404가 아닌 400으로 거절할 수 있어 오류 화면이 된다.
  - `/posts/..`·`/posts/%2E%2E`는 브라우저가 요청 전에 `/`로 정규화하므로 상세 페이지에 오지 않는다(목록 페이지가 열린다). 검사는 값이 어떤 경로로 들어왔는지와 무관하게 `params`에 온 값에 적용한다.
  - 8-1의 "형식이 다른 slug는 404"와 결과가 같다 (API는 형식을 검사하지 않고, 프론트는 호출 전에 걸러 같은 not-found를 보여 준다). Next가 `params`의 `%2F`·`%2E`를 디코딩하든 안 하든 `%`·`/`·`.`은 규칙에 맞지 않으므로 결과가 같다.
  - 관리자 폼의 Zod 스키마도 같은 상수(`SLUG_PATTERN`·`MAX_SLUG_LENGTH`)를 쓴다 ('관리자 글 작성·수정').
- `getPost`는 404(없는 글, DRAFT)면 `null`을 돌려주고('에러 처리'), 페이지는 `null`이면 `notFound()`. 그 외 오류는 그대로 던진다.
  - 목록 페이지와 같은 이유로 상태 코드는 `200`으로 남고 `noindex`가 붙는다.
- 표시 항목: 제목(`h1`), 카테고리 이름(있을 때, 카테고리 페이지 링크)·발행일과 그 아래 태그(`TagBadge`)를 `PostCard`와 같은 형식으로, 본문(`MarkdownRenderer`).
  - 요약·썸네일·수정일은 표시하지 않는다. 요약은 목록과 `description`·OG 설명용이고('Metadata'), 썸네일은 목록 페이지와 같은 이유다.
  - 태그를 목록과 같은 형식으로 둔다: 글에서 같은 태그의 다른 글로 가는 길이 상세에도 있어야 하고, 형식이 같아야 `PostCard`와 컴포넌트를 나눠 쓴다.
  - 글별 `<title>`·OG 태그는 'Metadata'.
- 댓글(Giscus): `<article>` 안의 `MarkdownRenderer` 뒤에 `<section aria-label="댓글" className="mt-12">`을 두고 그 안에 `components/post/Comments.tsx`(클라이언트 컴포넌트)를 둔다. 글을 찾았을 때만 그리므로 not-found 화면에는 없다.
  - `<article>` 안에 두는 이유: 댓글은 그 글에 딸린 내용이라(HTML 명세도 글의 댓글을 `<article>` 안에 둔다) 같은 요소로 묶고, `Post`가 요소 하나를 반환하는 구조도 그대로다. `mt-12`는 본문 끝과 댓글을 떼어 놓는 간격이다.
  - 저장소는 이 저장소(`mojunsul/blog`)의 Discussions, 카테고리는 `Comments`(Announcement 형식: 방문자는 새 Discussion을 직접 만들 수 없고 giscus 앱이 첫 댓글 때 만든다). 관리할 저장소를 늘리지 않으려고 별도 댓글 저장소를 두지 않는다. Discussions 활성화, giscus 앱 설치, 카테고리 생성은 관리자가 GitHub에서 하고, giscus.app 설정 화면에서 얻은 `repoId`·`categoryId`를 구현 전에 이슈에 남긴다.
  - 설정값(`repo`, `repoId`, `category`, `categoryId`)은 `Comments.tsx`의 상수로 둔다. 브라우저에 그대로 노출되는 공개 식별자라 비밀이 아니고, 환경변수(`NEXT_PUBLIC_*`)로 두면 빌드 때 값이 박히므로 이미지 빌드(12-7)에 값을 넘기는 절차만 늘어난다. 그래서 CI·빌드에 필요한 환경변수도 없다.
  - 매핑: `data-mapping="specific"`, `data-term="post-{id}"`(`PostDetailResponse.id`), `data-strict="1"`. slug는 바꿀 수 있어(8-4 'PUT') `pathname` 매핑이면 slug를 바꿀 때 댓글이 끊긴다. id는 바뀌지 않는다. giscus는 제목을 부분 일치로 찾으므로 `strict`가 없으면 `post-1`이 `post-12`의 Discussion과 섞일 수 있다 (`strict`는 term의 SHA-1로 정확히 찾는다).
  - 그 밖의 속성: `data-reactions-enabled="1"`, `data-emit-metadata="0"`, `data-input-position="bottom"`, `data-theme="preferred_color_scheme"`(사이트가 `prefers-color-scheme`로 다크 모드를 따르므로 같은 기준), `data-lang="ko"`, `data-loading="lazy"`, `crossorigin="anonymous"`, `async`.
  - 불러오기: `useEffect`에서 `https://giscus.app/client.js` `<script>`를 만들어 컨테이너 `div`(`ref`)에 붙이고, 정리 함수에서 그 컨테이너를 비운다(`replaceChildren()`). 비우면 스크립트와 giscus가 그 뒤에 만든 `.giscus` 컨테이너·iframe이 함께 지워진다. 정리가 없으면 `npm run dev`(React StrictMode가 effect를 마운트→정리→마운트로 두 번 실행)와 재마운트 때 스크립트·iframe이 겹쳐 붙는다. JSX의 `<script>`는 실행되지 않고, 몇 줄이라 `@giscus/react` 의존성을 더하지 않는다. 다른 글로 클라이언트 이동할 때 새 글의 댓글을 불러오도록 상세 페이지에서 `key={post.id}`로 다시 마운트한다.
  - 실패 처리: 스크립트를 받지 못하면(giscus.app 장애, 차단) 댓글 영역이 빈 채로 남고 글은 그대로 보인다. 재시도·오류 문구는 두지 않는다 (댓글은 부가 기능이고, iframe 안의 오류는 giscus가 직접 표시한다).
  - 글을 삭제해도 Discussion은 남는다. 관리자가 필요하면 GitHub에서 지운다.
  - `giscus.json`(댓글을 불러올 수 있는 origin 제한)은 두지 않는다. 다른 사이트가 공개 Discussion을 불러오는 것뿐이라 막을 이득이 작고, 로컬 개발(`localhost`)도 허용 목록에 넣어야 한다.
  - 로컬 개발도 같은 Discussions를 쓰고, 시드 글 id가 운영 글 id와 겹치면 같은 댓글이 보인다. 그래서 화면 확인에서 쓴 댓글의 Discussion은 확인 뒤 지운다.
  - 화면 확인 (로컬 `npm run dev` + 백엔드, 이슈 완료 조건):
    1. 글 상세 본문 아래 `section[aria-label="댓글"]` 안에 `iframe.giscus-frame`(`src`가 `https://giscus.app/ko/widget`으로 시작)이 생기고, 스크립트 요소의 `data-term`이 `post-{그 글 id}`, `data-strict`가 `1`이다. dev(StrictMode)에서도 그 안의 `script[src="https://giscus.app/client.js"]`와 `iframe.giscus-frame`은 하나씩이다.
    2. GitHub 로그인 후 댓글을 쓰면 Discussions의 `Comments` 카테고리에 제목 `post-{id}` Discussion이 생긴다.
    3. 관리자 화면에서 그 글의 slug를 바꾼 뒤 새 주소의 상세에 같은 댓글이 보인다. 확인 후 slug를 되돌리고 Discussion을 지운다.
    4. 상세에서 다른 글로 이동하면(목록으로 갔다가 다른 글) `section[aria-label="댓글"]` 안 스크립트 요소(하나)의 `data-term`이 새 글 id로 바뀐다.
    5. 없는 글(`/posts/nope`)의 not-found 화면에는 댓글 영역이 없다.
- 캐시: 기준은 '렌더링 전략'. 같은 글을 다시 요청하면 `getPost`와 `MarkdownRenderer`의 캐시로 백엔드 호출과 Shiki 하이라이팅이 다시 일어나지 않는다.
- not-found 화면: `app/(public)/not-found.tsx`를 둔다. 공개 페이지에서 부른 `notFound()`(상세의 없는 글, 목록의 잘못된 `page`)가 블로그 헤더 안에서 "페이지를 찾을 수 없습니다." 문구와 `/` 링크로 표시된다.
  - 어느 경로에도 맞지 않는 URL(`/foo`)은 루트 `app/not-found.tsx`가 맡는데, 루트 레이아웃만 거쳐 헤더가 없다. M5의 not-found 페이지 작업에서 함께 정하고, 그 전까지는 Next 기본 404 화면을 쓴다.

### 검색 페이지 (`/search`)
- **검색 입력**: `(public)/layout.tsx` 헤더의 블로그 이름 오른쪽에 둔다. `next/form`의 `<Form action="/search">` 안에 `<input type="search" name="q" maxLength={100} aria-label="검색어" placeholder="검색">`과 "검색" 제출 버튼을 둔다.
  - 문자열 `action`이라 GET으로 `/search?q=...`에 이동한다 (JS가 있으면 클라이언트 이동, 없으면 일반 폼 제출). 상태·이벤트 처리 코드를 두지 않아 레이아웃은 서버 컴포넌트로 남는다.
  - `maxLength`는 API `q` 상한(8-1, 100자)과 같다. 둘 다 UTF-16 단위로 센다. 빈 값 제출은 막지 않는다 (아래 안내 문구가 나온다).
  - 입력칸은 현재 검색어로 채우지 않는다. 사이트 안에서 검색하면 레이아웃이 유지되어 입력한 값이 남고, 주소로 바로 들어오면 비어 있다. 검색어는 결과 제목에 보인다.
- **URL**: `/search?q=키워드&page=N`. `page` 규칙은 '글 목록 페이지'와 같다 (`parsePageParam`, 1부터, 첫 페이지는 `page` 없음).
- **`q` 해석**: `lib/search.ts`의 순수 함수 `parseSearchQuery`. 앞뒤 공백을 지운(`trim()`) 값을 돌려준다. 없거나 공백만 있으면 `""`(검색어 없음), 같은 키가 여러 개이거나 지운 뒤 100자를 넘으면 `null`(잘못된 값).
  - API에는 지운 값을 보내므로 서버 `strip()`(8-1)이 받는 값은 이미 100자 이하다. 범위 밖 값을 API 전에 거르는 이유와 API 400을 `notFound()`로 바꾸지 않는 이유는 '글 목록 페이지'의 `page`와 같다. `trim()`과 `strip()`이 지우는 공백 문자 범위가 조금 달라(`trim()`은 NBSP도 지운다) 생기는 결과 차이는 받아들인다.
- **주소 만들기**: `searchHref(q, page)`(`lib/search.ts`)가 `URLSearchParams`로 `q`를 인코딩하고, `page`가 1이면 `page`를 붙이지 않는다. `Pagination`의 `pageHref`로 쓴다.
- **구성**: '글 목록 페이지'와 같다. `page.tsx`는 `Suspense`만 두고, 그 안의 비동기 컴포넌트가 `searchParams`를 읽어 아래 순서로 처리한다.
  | 입력 | 화면 |
  |---|---|
  | `page`가 잘못된 값, 또는 `q`가 `null` | `notFound()` |
  | `q`가 `""` (`/search`, `?q=`, `?q=%20`) | "검색어를 입력하세요." (API 호출 없음) |
  | 결과 있음 | 제목 "‘{q}’ 검색 결과 {totalElements}건", `PostList`, `Pagination` |
  | 첫 페이지 결과 없음 | "‘{q}’에 대한 검색 결과가 없습니다." |
  | `page` 2 이상인데 `content`가 빔 | `notFound()` |
  - 검색어는 React가 텍스트로 이스케이프해 표시한다. `notFound()`의 상태 코드·`noindex`는 '글 목록 페이지'와 같다.
  - `%`·`_`·`&`·`#`·한글도 그대로 검색된다 (`URLSearchParams` 인코딩, 일반 문자 검색은 8-1).
- **데이터**: `lib/api/posts.ts`의 `searchPosts(q, zeroBasedPage)`가 `/api/v1/posts?q=...&page=...`(`URLSearchParams`)를 호출한다. 오류는 '에러 처리'대로 그대로 던진다.
  - 캐시하지 않는다(`'use cache'` 없음). 검색어는 사용자가 마음대로 넣는 값이라 캐시 키가 끝없이 늘고 같은 키를 다시 쓸 일이 적다. 기본 캐시는 프로세스 메모리 LRU 하나(`cacheMaxMemorySize`)를 글 목록·상세·`MarkdownRenderer`와 나눠 쓰므로, 검색 결과가 그 항목들을 밀어낼 수 있다. 백엔드 검색은 수백 건 `like`(8-1)라 매번 호출해도 작다. 운영에서 느리면 측정 후 다시 검토한다.
- **색인 제외**: `search/page.tsx`에 `export const metadata = { robots: { index: false } }`를 둔다. 검색어마다 URL이 생기고 내용이 글 목록과 겹친다. 그 외 metadata는 'Metadata'.

### 카테고리·태그 페이지 (`/categories/{slug}`, `/tags/{name}`)
- **URL**: `/categories/{slug}?page=N`, `/tags/{name}?page=N`. `page` 규칙은 '글 목록 페이지'와 같다 (`parsePageParam`, 1부터, 첫 페이지는 `page` 없음).
- **주소 만들기**: `lib/taxonomy.ts`의 `categoryHref(slug, page)`·`tagHref(name, page)`. `page`가 1이면 `page`를 붙이지 않는다. `Pagination`의 `pageHref`, `PostCard`·상세의 카테고리 링크, `TagBadge`가 쓴다.
  - `tagHref`는 이름을 `encodeURIComponent`로 넣는다 (한글 태그). `categoryHref`는 slug가 영문 소문자·숫자·하이픈뿐(6장)이라 그대로 넣는다.
- **경로 값 해석**: Next 16.4의 `params`는 퍼센트 인코딩을 풀지 않은 값이다 (`/tags/스프링`의 `params.name`은 `%EC%8A%A4%ED%94%84%EB%A7%81`, `/tags/a%20b`는 `a%20b`. 16.4.2 `next dev`에서 확인).
  - 태그: `parseTagNameParam(value: string): string | null`(`lib/taxonomy.ts`)이 `decodeURIComponent`한 값을 돌려주고, 디코딩이 실패하면(`URIError`) `null`이다. 잘못된 인코딩(`%E0%A4%A`, `%`)은 Next가 페이지 전에 `400`으로 거절해 보통 오지 않지만, 오면 오류 화면 대신 `notFound()`가 되게 한다.
  - 태그 이름은 정규화(공백 제거·소문자)하지 않고 그대로 비교한다. `/tags/Spring`·`/tags/%20spring`은 목록에 없는 이름이라 `notFound()`다. 이유: 태그 이름이 경로의 식별자라 상세 `slug`(8-1)처럼 정확히 같은 값만 받고, 같은 내용이 여러 URL이 되지 않게 한다. 사이트 안 링크는 항상 API가 준 정규화된 이름으로 만든다.
  - 카테고리: 디코딩하지 않고 그대로 비교한다. slug에는 인코딩되는 문자가 없어 인코딩이 든 값은 어떤 slug와도 같지 않다 (`notFound()`).
- **존재 확인**: 글 목록 API는 없는 카테고리·태그도 `200` 빈 목록이라(8-1) "없음"과 "글 없음"을 가리지 못하므로, 페이지가 목록 API로 먼저 확인한다.
  - 카테고리: `getPublicCategories()`(`GET /api/v1/categories`)에서 `slug`가 같은 항목. 글이 0개인 카테고리도 목록에 있어(8-1) 페이지가 열린다.
  - 태그: `getPublicTags()`(`GET /api/v1/tags`)에서 `name`이 같은 항목. PUBLISHED 글이 없는 태그는 목록에 없어(8-1) `notFound()`다. 그래서 DRAFT에만 달린 태그 이름이 페이지 존재 여부로도 드러나지 않는다.
  - 찾은 항목의 `slug`·`name`만 `getPosts`의 필터로 넘긴다. 필터 캐시 키가 실제 카테고리·태그 수로 묶이므로 검색(`searchPosts`)과 달리 캐시한다 ('렌더링 전략').
- **데이터**
  - `lib/api/taxonomy.ts`(새 파일, `server.ts` 사용): `getPublicCategories()`, `getPublicTags()`. 캐시는 '렌더링 전략'의 데이터 함수와 같다. `Public`을 붙인 이유는 브라우저용 `admin-categories.ts`의 `getCategories`(캐시 없음)와 이름이 겹치지 않게 하려는 것이다.
  - `getPosts(zeroBasedPage, filter = {})`(`filter: { category?: string; tag?: string }`): `URLSearchParams`로 `page`와 값이 있는 필터만 붙인다. '글 목록 페이지'는 `filter` 없이 부른다.
  - 오류는 '에러 처리'대로 그대로 던진다 (오류 화면). 목록 조회 404는 없으므로 `null` 변환을 두지 않는다.
- **구성**: '글 목록 페이지'와 같다. `page.tsx`는 `Suspense`만 두고, 그 안의 비동기 컴포넌트가 `params`·`searchParams`를 읽어 아래 순서로 처리한다 (앞 줄에 걸리면 뒤는 하지 않는다).
  | 입력 | 화면 |
  |---|---|
  | `page`가 잘못된 값 | `notFound()` (API 호출 없음) |
  | 태그: `parseTagNameParam`이 `null` | `notFound()` (API 호출 없음) |
  | 목록(`getPublicCategories`·`getPublicTags`)에 없는 slug·이름 | `notFound()` (글 API 호출 없음) |
  | 결과 있음 | 제목(`h1`) "{name} 카테고리 글 {totalElements}건" / "#{name} 태그 글 {totalElements}건", `PostList`, `Pagination` |
  | 첫 페이지 결과 없음 | "{name} 카테고리에 아직 글이 없습니다." / "#{name} 태그가 달린 글이 없습니다." |
  | `page` 2 이상인데 `content`가 빔 | `notFound()` |
  - `{name}`은 목록 API의 카테고리 `name`, 태그 `name`이다. React가 텍스트로 이스케이프해 표시한다.
  - 태그 페이지의 첫 페이지 빈 결과는 태그 목록과 글 목록의 캐시가 따로 갱신되어(각 60초) 생길 수 있다 (태그가 달린 유일한 글을 DRAFT로 바꾼 직후 등). 카테고리도 같은 이유로 지운 카테고리가 잠시 빈 페이지로 열릴 수 있다. 반영 지연은 '렌더링 전략'과 같이 받아들인다.
  - `notFound()`의 상태 코드·`noindex`는 '글 목록 페이지'와 같다. 검색 페이지와 달리 색인을 막지 않는다 (분류마다 고정된 주소 하나다). 그 외 metadata는 'Metadata'.
- **`TagBadge`** (`components/post/TagBadge.tsx`, 상태 없는 서버 컴포넌트): `name`과 선택 `postCount`를 받아 `tagHref(name, 1)`로 가는 `Link` "#{name}"을 그린다. `postCount`가 있으면 "#{name} ({postCount})". 글 목록·상세의 태그(개수 없음)와 '글 목록 페이지'의 태그 목록(개수 있음)이 쓴다.

**화면 확인** (백엔드 `local` 프로필 + 시드 데이터 + 관리자 계정으로 `next dev`를 띄워 DOM·`curl`로 본다. 완료 조건의 확인 수단이다. 시드에는 태그가 없으므로 먼저 관리자 글 수정 화면에서 PUBLISHED 시드 글 하나(Backend 카테고리)에 `spring, 스프링`, 다른 PUBLISHED 글 하나에 `spring`, DRAFT 시드 글에 `draft-only`를 붙이고, 데이터 캐시(60초)가 지난 뒤 새로고침해서 본다)
1. `/`: 글 목록 위 `nav[aria-label="태그"]`에 "#spring (2)", "#스프링 (1)" 순서, `draft-only` 없음. `PostCard`의 카테고리 이름 `href`가 `/categories/backend`, 태그 `href`가 `/tags/spring`·`/tags/%EC%8A%A4%ED%94%84%EB%A7%81`이고 태그 없는 글은 태그 줄이 없다. `/?page=abc` → not-found 화면, 태그 `nav` 없음
2. `/categories/backend` → "Backend 카테고리 글 {n}건"과 Backend 글만. `/categories/infra`(시드의 글 없는 카테고리) → "Infra 카테고리에 아직 글이 없습니다." `/categories/Backend`, `/categories/nope`, `/categories/backend?page=0`, `/categories/backend?page=2` → not-found 화면
3. 태그 링크 `#스프링` 클릭 → "#스프링 태그 글 1건". `/tags/spring` → 2건. `/tags/Spring`, `/tags/%20spring`, `/tags/draft-only`, `/tags/nope`, `/tags/spring?page=2` → not-found 화면
4. 글 상세: 카테고리 이름이 `/categories/{slug}` 링크, 그 아래 태그 `TagBadge`가 `PostCard`와 같은 순서로 보인다. 태그 없는 글은 태그 줄이 없다
5. `.env.local` 없이 `npm run build`와 `npx next typegen` 성공 ('관리자 인증' 8번과 같다)
- 페이지 이동 링크는 `categoryHref`·`tagHref` 단위 테스트와 기존 `Pagination`으로 보고 화면에서 따로 보지 않는다 (시드 글이 한 페이지 크기 10개보다 적다).

### Metadata
- **사이트 주소 `SITE_URL`**: 절대 URL(canonical, `og:url`, M5 sitemap·RSS)의 기준이다. 저장소에 도메인을 두지 않으므로(12-3) 실행 시점 환경변수로 받는다. `API_INTERNAL_URL`('API 주소 처리')과 같이 `NEXT_PUBLIC_`이 아니고 기본값이 없다.
  - `lib/site.ts`(`server-only`)의 `siteUrl(): URL`이 `new URL(process.env.SITE_URL)`을 돌려준다. 값이 없으면 `server.ts`와 같은 이유로 `"SITE_URL이 설정되지 않았습니다. frontend/.env.example을 .env.local로 복사하세요."` 오류를 던지고, 형식이 틀리면(`blog.도메인`처럼 scheme 없음) `new URL`의 `TypeError`를 그대로 던진다. 둘 다 그 페이지는 오류 화면이다. 끝의 `/` 유무는 Next가 맞춘다.
  - 로컬: `frontend/.env.example`에 `SITE_URL=http://localhost:3000`을 더한다. 운영: compose가 `infra/.env`에서 받는다 (12-2).
  - `siteUrl()`은 요청 시점 API(`params`)를 읽은 뒤에만 부른다. 루트 layout에서 부르면 빌드 때 실행되어 값이 없는 CI 빌드가 실패하고, 값이 있어도 이미지에 박힌다. 그래서 `metadataBase`는 루트가 아니라 절대 URL이 필요한 글 상세의 `generateMetadata`에만 둔다. 그 밖에 상대 URL 필드를 쓰는 곳은 `(public)/layout.tsx`의 RSS `alternates.types`뿐이고, `metadataBase` 없이 상대 주소 그대로 나온다('RSS 피드·푸터').
  - 요청의 `Host` 헤더로 주소를 만들지 않는다: Nginx가 `server_name _`(12-3)라 IP나 임의 `Host`로 들어온 요청이 그 값을 canonical로 받는다.
- **사이트 공통** (`app/layout.tsx`의 정적 `metadata`): `lib/site.ts`의 상수 `SITE_NAME = "Blog"`, `SITE_DESCRIPTION = "개인 개발 블로그"`를 쓴다 (`(public)/layout.tsx` 헤더의 블로그 이름도 `SITE_NAME`).
  - `title: { default: SITE_NAME, template: \`%s | ${SITE_NAME}\` }`, `description: SITE_DESCRIPTION`, `openGraph: SITE_OPEN_GRAPH`(`lib/site.ts`, `{ siteName: SITE_NAME, locale: "ko_KR", type: "website" }`).
  - `openGraph`는 하위 페이지가 정하면 통째로 바뀌므로(얕은 병합), 글 상세는 `SITE_OPEN_GRAPH`를 펼친 뒤 덮어쓴다. 루트에 `og:title`을 두지 않는다: 하위 페이지가 `openGraph`를 정하지 않으면 모든 페이지가 같은 `og:title`을 물려받는다. 없으면 공유 미리보기는 `<title>`을 쓴다.
  - 관리자 페이지는 따로 정하지 않는다 (`Blog`).
- **페이지별**
  | 페이지 | `title` | 그 외 |
  |---|---|---|
  | `/` (모든 `page`) | 없음 (`Blog`) | 없음 |
  | 글 상세 | `post.title` | 아래 |
  | `/categories/{slug}` | `"{name} 카테고리"` | 없음 |
  | `/tags/{name}` | `"#{name} 태그"` | 없음 |
  | `/search` | `"검색"` | 기존 `robots: { index: false }` ('검색 페이지') |
  - 카테고리·태그·상세는 `generateMetadata`에서 페이지와 같은 순서로 확인한다 (상세: `isValidSlug` → `getPost`, 태그: `parseTagNameParam` → `getPublicTags`, 카테고리: `getPublicCategories`). 확인에 걸리면 `{}`를 돌려준다(`title`은 `Blog`). 페이지가 같은 확인으로 `notFound()`하고 Next가 `noindex`를 넣는다. `page` 값은 보지 않는다 (잘못된 `page`도 페이지의 `notFound()`가 맡는다).
  - 같은 요청에서 페이지와 같은 인자로 부르므로 `'use cache'` 데이터 함수('렌더링 전략')가 백엔드를 한 번만 부른다. 조회가 실패하면 그대로 던져 오류 화면이다 ('에러 처리').
  - 목록·카테고리·태그 페이지에는 canonical을 두지 않는다. 사이트 안 링크는 첫 페이지를 `page` 없는 주소로만 만들어(`pageHref`) `?page=1`은 손으로 친 주소뿐이고, 색인할 주소는 M5 sitemap이 알린다. 2쪽 이후 `title`에 쪽 번호를 붙이지 않는다.
- **글 상세** `generateMetadata`
  - `metadataBase: siteUrl()`, `title: post.title`, `description: post.summary ?? SITE_DESCRIPTION`(500자 이하, 8-4. 자르지 않는다), `alternates: { canonical: \`/posts/${slug}\`, types: RSS_ALTERNATE_TYPES }` (`types`는 'RSS 피드·푸터')
  - `openGraph: { ...SITE_OPEN_GRAPH, type: "article", title: post.title, description: 위와 같은 값, url: 같은 경로, publishedTime: post.publishedAt }`. `slug`는 `isValidSlug`를 통과한 값이라 그대로 경로에 넣는다.
  - `og:image`·`twitter:*`는 두지 않는다. 썸네일은 M6이고, X는 `twitter:*`가 없으면 OG 태그를 쓴다. 수정일(`article:modified_time`)·태그(`article:tag`)도 두지 않는다 (화면에 없는 값, '글 상세 페이지').
  - 본문 컴포넌트도 `params`를 읽어 요청 시점에 렌더링되므로 metadata는 Next가 본문과 함께 스트리밍한다 (Cache Components의 `generateMetadata` 규칙). 공유 미리보기 봇(`facebookexternalhit`·`Twitterbot` 등, Next `htmlLimitedBots` 기본값)에는 Next가 `<head>`에 넣어 보낸다.

**화면 확인** (백엔드 `local` 프로필 + 시드 데이터로 `.env.local`에 `SITE_URL=http://localhost:3000`을 두고 `next dev`를 띄워 `curl`로 본다. 완료 조건의 확인 수단이다. 요약 없는 글은 관리자 수정 화면에서 시드 글 하나의 요약을 지워 만든다)
1. 요약 있는 PUBLISHED 글 `curl -s -A Twitterbot /posts/{slug}`: `<head>` 안에 `<title>{제목} | Blog</title>`, `description`이 요약, `<link rel="canonical" href="http://localhost:3000/posts/{slug}">`, `og:title`·`og:description`·`og:url`(canonical과 같음)·`og:type` `article`·`article:published_time`(`publishedAt`)·`og:site_name` `Blog`·`og:locale` `ko_KR`
2. 요약 없는 글: `description`·`og:description`이 `개인 개발 블로그`
3. `/` → `<title>Blog</title>`, canonical 없음, `og:type` `website`. `/categories/backend` → `Backend 카테고리 | Blog`, `/tags/spring` → `#spring 태그 | Blog`, `/search?q=a` → `검색 | Blog`와 `noindex`. 셋 다 canonical 없음
4. `/posts/nope`, DRAFT 글 slug, `/tags/nope` → `<title>Blog</title>`과 `noindex`
5. `.env.local`에서 `SITE_URL`을 지우고 `next dev`를 다시 띄우면 글 상세가 오류 화면이고 `/`는 열린다. `.env.local` 없이 `npm run build`·`npx next typegen` 성공 ('카테고리·태그 페이지' 5번과 같다)
6. 운영 compose: `SITE_URL= POSTGRES_PASSWORD=x docker compose -f infra/docker-compose.prod.yml config`가 `SITE_URL is required`로 실패한다 (셸 값이 `infra/.env`보다 우선한다)
7. 배포(머지 뒤, 12-6): Secrets에 `SITE_URL`을 머지 전에 등록하고, 머지 후 deploy가 성공한 뒤 `curl -s https://blog.도메인/posts/{slug}`의 canonical이 `https://blog.도메인/posts/{slug}`다

### sitemap·robots (`/sitemap.xml`, `/robots.txt`)
- **사이트 주소**: 'Metadata'의 `siteUrl()`(`SITE_URL`, 운영 연결 포함)을 쓴다. 주소는 `new URL(경로, siteUrl()).href`로 만든다.
- **요청 시점 실행**: `sitemap.ts`·`robots.ts`는 기본으로 빌드 때 만들어지는 특수 Route Handler라, 둘 다 첫 줄에서 `await connection()`(`next/server`)을 부른 뒤 `siteUrl()`과 데이터 함수를 부른다. 빠뜨리면 빌드 중에 `SITE_URL` 없음 오류나 백엔드 호출로 빌드가 실패한다 ('빌드 시 API 의존 제거').
- **robots** (`app/robots.ts`): `rules: { userAgent: "*", allow: "/", disallow: ["/admin", "/api/"] }`, `sitemap: new URL("/sitemap.xml", SITE_URL).href`.
  - `/admin`: 관리자 화면. `/api/`: JSON API라 색인할 내용이 없다.
  - `/search`는 막지 않는다. 막으면 크롤러가 페이지의 `noindex`('검색 페이지')를 읽지 못해, 외부 링크가 있으면 주소만 색인될 수 있다.
- **sitemap** (`app/sitemap.ts`)에 넣는 주소와 `lastModified` (순서는 아래 줄 순서, 각 묶음 안은 API 순서)
  | 주소 | 대상 | `lastModified` |
  |---|---|---|
  | `/` | 항상 | 없음 |
  | `/posts/{slug}` | 모든 PUBLISHED 글 | 글 `updatedAt` |
  | `categoryHref(slug, 1)` | `getPublicCategories()` 중 `postCount > 0` | 없음 |
  | `tagHref(name, 1)` | `getPublicTags()` 전부 (PUBLISHED 글이 있는 태그만 온다, 8-1) | 없음 |
  - 글이 0개인 카테고리를 빼는 이유: "아직 글이 없습니다"만 있는 페이지다. 페이지 2 이상(`?page=N`)과 `/search`는 넣지 않는다 (목록과 내용이 겹친다).
  - 목록·분류 페이지에 `lastModified`를 두지 않는 이유: 그 페이지가 바뀐 시각을 주는 값이 없고, 부정확한 값은 검색엔진이 무시하게 만든다. `changeFrequency`·`priority`도 두지 않는다 (Google이 쓰지 않는다).
  - 글 `updatedAt`은 글 행이 바뀐 시각이라 태그만 바꾼 수정은 반영되지 않는다 (8-4 `PUT`). 받아들인다.
  - XML 이스케이프는 Next가 하고, 한글 태그는 `tagHref`가 퍼센트 인코딩한다.
- **데이터**: 모든 PUBLISHED 글은 공개 목록 API(8-1)를 `size=50`(최대, 7장)으로 `hasNext`가 `false`일 때까지 `page`를 0부터 올려 가며 읽는다. 별도 백엔드 API는 두지 않는다: 글이 수백 건이라 몇 번의 호출이면 되고, 목록 응답에 `updatedAt`만 더하면 된다 (8-1).
  - `lib/api/posts.ts`의 `getAllPublishedPosts()`. 캐시는 '렌더링 전략'의 데이터 함수와 같다 (전체 결과를 한 번에 캐시). 카테고리·태그는 기존 `getPublicCategories()`·`getPublicTags()`.
  - 읽는 도중 글이 발행·삭제되면 페이지 경계가 밀려 한 글이 빠지거나 두 번 들어갈 수 있다. 다음 갱신(60초)에 맞춰지므로 받아들이고 중복을 거르지 않는다.
  - 항목 만들기는 순수 함수 `buildSitemap(siteUrl, posts, categories, tags): MetadataRoute.Sitemap`(`lib/sitemap.ts`)로 나눠 단위 테스트한다. `sitemap.ts`는 데이터 함수 셋을 `Promise.all`로 부르고 이 함수에 넘긴다.
- **실패**: API 오류·네트워크 오류는 '에러 처리'대로 그대로 던져 `/sitemap.xml`이 `500`이다 (빈 sitemap을 돌려주지 않는다: 크롤러가 글이 없어진 것으로 보지 않게). `SITE_URL` 누락은 두 경로 모두 `500`이고 나머지 화면은 영향이 없다.

**화면 확인** (백엔드 `local` 프로필 + 시드 데이터로 `next dev`를 띄워 `curl`로 본다. 완료 조건의 확인 수단이다. 시드에 태그가 없으면 '카테고리·태그 페이지' 화면 확인처럼 PUBLISHED 글 하나에 `스프링`, DRAFT 글에 `draft-only`를 붙이고 60초 뒤 한 번 요청해 갱신을 시작한 뒤 다시 요청해 본다('렌더링 전략'))
1. `.env.local`에 `SITE_URL=http://localhost:3000`: `curl -s localhost:3000/robots.txt`에 `User-Agent: *`, `Allow: /`, `Disallow: /admin`, `Disallow: /api/`, `Sitemap: http://localhost:3000/sitemap.xml`
2. `curl -s localhost:3000/sitemap.xml`: `<loc>http://localhost:3000/</loc>`(lastmod 없음), PUBLISHED 시드 글마다 `<loc>…/posts/{slug}</loc>`와 그 글 상세 API의 `updatedAt`과 같은 `<lastmod>`, DRAFT 글 없음, `/categories/backend` 있음·`/categories/infra`(글 없는 카테고리) 없음, `/tags/%EC%8A%A4%ED%94%84%EB%A7%81` 있음·`draft-only` 없음
3. `SITE_URL`을 빼고 `next dev`를 다시 띄우면 두 경로가 `500`이고 `/`는 정상
4. `.env.local` 없이 `npm run build` 성공 ('관리자 인증' 8번과 같다)
5. 운영 연결: 'Metadata' 화면 확인 6·7과 같다 (Secret이 없으면 compose 변수 검사로 배포가 실패한다). 머지 후 deploy가 성공한 뒤 `curl -s https://blog.도메인/robots.txt`의 `Sitemap:`이 `https://blog.도메인/sitemap.xml`이고 `curl -s -o /dev/null -w '%{http_code}' https://blog.도메인/sitemap.xml`이 `200`이다.
- 51건 이상(두 번째 페이지 호출)은 시드 글이 적어 화면에서 보지 않는다. 반복 조건은 `PageResponse`의 `hasNext` 하나다.

### RSS 피드·푸터 (`/rss.xml`)
- **형식**: RSS 2.0. Atom은 두지 않는다 (피드 리더가 모두 읽고, 한 형식이면 된다).
- **Route Handler** (`app/rss.xml/route.ts`의 `GET`): 첫 줄에서 `await connection()`을 부른 뒤 `siteUrl()`과 `getAllPublishedPosts()`를 부른다 (이유는 'sitemap·robots'의 '요청 시점 실행'과 같다). `buildRss`의 결과를 `new Response(xml, { headers: { "Content-Type": "application/rss+xml; charset=utf-8" } })`로 돌려준다.
  - 데이터는 sitemap과 같은 `getAllPublishedPosts()`(공개 목록 API 순서 = 최신 발행 순, 8-1)다. 캐시도 그 함수의 것('렌더링 전략')이라 반영 지연이 같고 sitemap과 캐시를 함께 쓴다. RSS용 API·데이터 함수는 따로 두지 않는다.
  - **실패**: 'sitemap·robots'의 '실패'와 같다 (API 오류·`SITE_URL` 누락은 `500`, 빈 피드를 돌려주지 않는다). 글이 0개면 `<item>` 없는 정상 피드다.
- **XML 만들기**: 순수 함수 `buildRss(siteUrl: string, channel: { title, description }, posts): string`(`lib/rss.ts`)로 나눠 단위 테스트한다. `lib/site.ts`는 `server-only`라 테스트에서 불러올 수 없어 채널 값은 Route Handler가 `SITE_NAME`·`SITE_DESCRIPTION`을 넘긴다. 같은 파일에 `RSS_PATH = "/rss.xml"`을 둔다 (푸터·alternate가 같이 쓴다).
  - 항목 수: 앞에서부터 **20개** (`posts.slice(0, 20)`). 피드 리더는 새 글만 가져가므로 전체가 필요 없고, 응답 크기가 글 수와 함께 늘지 않게 한다.
  - 문서: `<?xml version="1.0" encoding="UTF-8"?>` 다음 `<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom"><channel>`.
  - 채널: `title`(`SITE_NAME`), `link`(`/`의 절대 주소), `description`(`SITE_DESCRIPTION`), `language` `ko`, `<atom:link href="{RSS_PATH 절대 주소}" rel="self" type="application/rss+xml"/>`(RSS 권고 사항, W3C 피드 검사기가 없으면 경고). `lastBuildDate`·`ttl`은 두지 않는다 (피드가 바뀐 시각을 주는 값이 없다, 'sitemap·robots'의 `lastModified`와 같은 이유).
  - 항목(`<item>`, API 순서)
    | 요소 | 값 |
    |---|---|
    | `title` | `title` |
    | `link` | `/posts/{slug}`의 절대 주소 |
    | `guid isPermaLink="false"` | `post-{id}` |
    | `description` | `summary`. `null`이면 요소를 두지 않는다 |
    | `category` | `category.name`. `null`이면 요소를 두지 않는다 |
    | `pubDate` | `new Date(publishedAt).toUTCString()` (RFC 822 형식, 예 `Sun, 11 Oct 2026 01:00:00 GMT`) |
  - `guid`를 주소가 아닌 `id`로 두는 이유: slug를 바꾸면 주소가 바뀌어 리더가 같은 글을 새 글로 다시 띄운다 (Giscus `post-{id}` 매핑과 같은 이유, '글 상세 페이지').
  - 본문(`content`)은 넣지 않는다: 목록 API에 없고(8-1), 요약과 링크로 사이트에서 읽게 한다. 태그는 넣지 않는다.
  - 주소는 `new URL(경로, siteUrl).href`로 만든다 ('sitemap·robots'와 같다). slug는 6장 규칙상 인코딩할 문자가 없다.
- **XML 이스케이프**: 문자열 값(채널 값·`title`·`summary`·`category.name`·주소)은 모두 `escapeXml`(`lib/rss.ts`, 내보내지 않음)을 거친다. 아래 금지 문자를 먼저 지우고, 그다음 `&` → `&amp;`를 먼저 한 뒤 `<` `>` `"` `'`를 엔티티로 바꾼다. CDATA는 쓰지 않는다 (`]]>`가 든 값을 따로 처리해야 한다).
  - XML 1.0에서 쓸 수 없는 문자(U+0000~0008, U+000B, U+000C, U+000E~001F, U+FFFE, U+FFFF)는 `escapeXml` 안에서 지우므로 위 문자열 값 모두에 적용된다. 하나라도 있으면 피드 전체가 파싱 오류인데, `summary`는 NUL만 막아(8-4) 다른 제어 문자가 들어올 수 있고, 제목·카테고리 이름의 `\P{Cc}`(8-4)도 U+FFFE·U+FFFF(`Cn`)는 통과시킨다.
- **`<link rel="alternate">`**: `(public)/layout.tsx`에 정적 `metadata = { alternates: { types: RSS_ALTERNATE_TYPES } }`를 둔다. `RSS_ALTERNATE_TYPES = { "application/rss+xml": RSS_PATH }`(`lib/rss.ts`).
  - 상대 주소를 쓴다: layout은 빌드 때 metadata가 만들어져 `siteUrl()`을 부를 수 없다('Metadata'). `metadataBase`가 없으면 Next는 `alternates`의 상대 주소를 그대로 출력하고(`href="/rss.xml"`), 피드 탐지는 페이지 주소 기준으로 푼다.
  - 하위 페이지가 `alternates`를 정하면 통째로 바뀐다(`openGraph`와 같은 얕은 병합). 그래서 `alternates`를 정하는 글 상세는 `types: RSS_ALTERNATE_TYPES`를 함께 넣는다('Metadata'). 글 상세에는 `metadataBase`가 있어 절대 주소로 나온다.
  - 관리자 페이지에는 두지 않는다 (루트 layout이 아니라 `(public)` layout).
- **푸터** (`(public)/layout.tsx`, `<main>` 뒤): 헤더와 같은 너비(`max-w-3xl`, `px-4`)에 위 테두리(`border-t`)를 두고 `RSS` 링크 하나만 둔다. `<a href={RSS_PATH}>`로 쓴다 (`next/link`는 페이지 이동·prefetch용이라 XML 경로에는 일반 링크). 저작권 연도는 두지 않는다 (`new Date()`는 정적 셸에서 빌드 시각으로 고정된다).
  - `(public)` 안의 `not-found.tsx` 화면에도 같은 layout이라 푸터가 나온다.

**화면 확인** (백엔드 `local` 프로필 + 시드 데이터로 `.env.local`에 `SITE_URL=http://localhost:3000`을 두고 `next dev`를 띄워 `curl`로 본다. 완료 조건의 확인 수단이다. 이스케이프는 관리자 화면에서 PUBLISHED 시드 글 하나의 제목을 `A & B <C>`로 바꾸고 60초 뒤 한 번 요청해 갱신을 시작한 뒤 다시 요청해 본다('렌더링 전략'))
1. `curl -s -D - localhost:3000/rss.xml`: `Content-Type: application/rss+xml; charset=utf-8`, `<rss version="2.0"`, 채널 `title` `Blog`·`link` `http://localhost:3000/`·`language` `ko`·`atom:link` `http://localhost:3000/rss.xml`, PUBLISHED 시드 글마다 `<item>`(공개 목록 API와 같은 순서)과 `link` `http://localhost:3000/posts/{slug}`·`guid` `post-{id}`·`pubDate` `… GMT`, DRAFT 글 없음
2. 바꾼 제목이 `<title>A &amp; B &lt;C&gt;</title>`이고 `xmllint --noout`(또는 `python3 -c 'import sys,xml.dom.minidom as m; m.parseString(sys.stdin.buffer.read())'`)가 오류 없이 끝난다
3. `curl -s localhost:3000/` · `/posts/{slug}`의 `<head>`에 `<link rel="alternate" type="application/rss+xml" href="…/rss.xml">`가 있고(목록은 `/rss.xml`, 상세는 `http://localhost:3000/rss.xml`), 상세의 canonical도 그대로 있다. `/admin/login`에는 없다
4. 공개 페이지 아래에 `RSS` 링크가 있고 `href="/rss.xml"`이다. 관리자 페이지에는 푸터가 없다
5. `SITE_URL`을 빼고 `next dev`를 다시 띄우면 `/rss.xml`이 `500`이고 `/`는 정상. `.env.local` 없이 `npm run build` 성공 ('sitemap·robots' 화면 확인 3·4와 같다)
6. 운영 연결: 머지 후 deploy가 성공한 뒤 `curl -s https://blog.도메인/rss.xml`의 `atom:link`가 `https://blog.도메인/rss.xml`이다 ('sitemap·robots' 화면 확인 5와 같은 `SITE_URL` 연결)
- 21건 이상(20개 자르기)은 시드 글이 적어 화면에서 보지 않고 단위 테스트로 본다.

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
- Vitest로 `lib/`의 로직이 있는 순수 함수만 단위 테스트한다 (`date.ts`, `errors.ts`의 파싱 함수, `pagination.ts`, `post-status.ts`, `post-id.ts`, `slug.ts`, `cookie.ts`, `next-path.ts`, `single-flight.ts`, `search.ts`, `tags.ts`, `taxonomy.ts`, `sitemap.ts`, `rss.ts`).
  - `tags.ts`: `parseTags`(쉼표·앞뒤 공백·빈 조각·대소문자·중복과 순서, `İ`처럼 소문자로 바꾸면 길어지는 문자), `validateTags`(10·11개, 50·51자는 소문자로 바꾼 뒤 기준, 한글·숫자·`node-js` 통과, `-a`·`a-`·`a--b`·`a_b`·`a b` 거절, 여러 규칙을 어기면 개수 초과가 먼저이고 그다음은 항목 순서대로 항목마다 길이 → 패턴인 첫 메시지: 11개 중 첫 항목이 51자면 개수 메시지, `["a_b", "x"×51]`이면 "태그는 문자·숫자·하이픈만 쓸 수 있습니다", `["x"×51 + "_", "a_b"]`이면 "태그는 50자 이하여야 합니다")
  - `sitemap.ts`: `buildSitemap`(위 표의 주소 순서, `SITE_URL` 끝 `/` 유무와 무관한 절대 주소, 글 `lastModified`는 `updatedAt`, `postCount` 0 카테고리 제외, 한글 태그 인코딩, 글·카테고리·태그가 모두 비면 `/` 하나)
  - `rss.ts`: `buildRss`(채널 값과 `atom:link`, 항목 요소와 순서, 21개면 앞 20개, `summary`·`category`가 `null`이면 요소 없음, `pubDate` 형식, `SITE_URL` 끝 `/` 유무와 무관한 절대 주소, `&<>"'`가 든 제목·요약·카테고리 이스케이프(`&lt;`가 `&amp;lt;`로 두 번 바뀌지 않음), 요약의 `\u0001`·`\uFFFF`와 제목·카테고리의 `\uFFFF` 제거, 줄바꿈·탭 유지, 글이 없으면 `<item>` 없음)
  - `taxonomy.ts`: `categoryHref`·`tagHref`(`page` 1이면 `page` 없음, 2면 `?page=2`, 한글 태그는 `encodeURIComponent`, `node-js`는 그대로), `parseTagNameParam`(인코딩된 한글 → 한글, `a%20b` → `a b`, `Spring`은 바꾸지 않음, `%E0%A4%A`·`%` → `null`)
  - `async` 서버 컴포넌트는 Vitest·Jest 모두 단위 테스트를 지원하지 않으므로 단위 테스트하지 않는다.
  - `server.ts`는 `server-only`가 테스트 환경에서 import 시 오류를 던지므로 단위 테스트하지 않는다 (실제 호출은 화면에서 확인).
  - `client.ts`와 `auth.ts`·`admin-posts.ts`·`admin-categories.ts`(브라우저 전역·`fetch`에 기대는 파일), `loginSchema`·`postSchema`·`categorySchema`(라이브러리 규칙을 그대로 선언)는 단위 테스트하지 않는다. 흐름은 '관리자 인증'·'관리자 글 목록'·'관리자 글 작성·수정'·'관리자 카테고리'의 화면 확인으로 본다.
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

