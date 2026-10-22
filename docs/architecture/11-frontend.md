## 11. 프론트엔드 구조

**App Router 규칙 요약**
- 폴더 = URL 경로, `page.tsx` = 해당 경로의 화면, `layout.tsx` = 공유 레이아웃
- `[slug]` = 동적 경로, `(group)` = URL에 나타나지 않는 레이아웃 그룹
- Next.js 16부터 기존 `middleware.ts`는 `proxy.ts`로 이름이 바뀌었다. 설치 버전에 맞춰 사용한다.

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
│  │     ├ layout.tsx               # 관리자 레이아웃
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
│  └ admin/         # PostForm, MarkdownPreview
├ lib/
│  ├ api/
│  │  ├ server.ts   # 서버 컴포넌트용 fetch (API_INTERNAL_URL, server-only)
│  │  ├ client.ts   # (M2) 브라우저용 fetch (/api/v1, CSRF 헤더, 401 시 refresh 후 재시도)
│  │  ├ errors.ts   # ProblemDetail 타입(명세에 없어 직접 정의)·파싱, ApiError
│  │  ├ posts.ts
│  │  └ auth.ts
│  ├ date.ts        # UTC → KST 표시 변환 (formatDate, YYYY.MM.DD)
│  ├ pagination.ts  # URL의 page 쿼리 해석 (parsePageParam)
│  ├ slug.ts        # slug 형식 검사 (isValidSlug, 6장 slug 규칙)
│  └ utils.ts
├ schemas/
│  └ post.ts        # Zod 스키마 (postSchema, categorySchema, loginSchema) - 서버 검증 규칙과 동일
├ types/
│  └ api.d.ts       # openapi-typescript 생성 파일 (직접 수정 금지)
└ proxy.ts          # /admin/** 접근 시 access_token 쿠키 없으면 /admin/login으로 이동
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
- `proxy.ts`의 쿠키 체크는 화면 이동 편의용이다. **실제 권한 검증은 Spring Security가 담당한다.**
- 관리자가 글을 저장·수정·삭제해도 공개 페이지는 데이터 캐시 주기에 따라 최대 60초 후 반영된다(본문이 바뀌면 `MarkdownRenderer`의 캐시 키도 바뀐다). 즉시 반영(on-demand revalidate)은 M5 이후 검토한다.

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
  - 로컬에서는 `next.config`의 `rewrites`로 `/api/*` → `http://localhost:8080/api/*` (M2, `client.ts`와 함께 추가)

### 에러 처리
- 2xx가 아닌 응답은 `ApiError`(`status`, `code`, `detail`, `errors`)로 바꾼다. 본문이 ProblemDetail이면 파싱하고, 아니면(Nginx 502 HTML 등) `code: null`과 일반 메시지를 넣는다.
- 네트워크 오류(백엔드 다운 등)는 감싸지 않고 그대로 던져 Next 오류 화면으로 보낸다.
- 페이지에서 `ApiError`의 `status === 404`면 `notFound()`를 호출한다.
  - 단, `'use cache'` 함수 안에서 던진 오류는 캐시 경계를 넘으며 일반 `Error`가 되어 `instanceof ApiError`와 `status`를 잃는다. 그래서 캐시하는 단건 조회 함수(`getPost`)는 404를 함수 안에서 `null`로 바꿔 돌려주고, 페이지는 `null`이면 `notFound()`를 호출한다. 그 외 오류는 그대로 던진다 (오류 화면으로 가므로 `status`가 없어도 된다).

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
- 페이지 이동(`Pagination`): "이전"·"다음" 링크와 현재 위치(`2 / 3`)만 둔다. "이전"은 `page > 1`, "다음"은 `hasNext`일 때 보이고, 전체가 한 페이지 이내면 영역을 숨긴다. 페이지 번호 목록은 글이 많아지면 검토한다.
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
  - 결과가 인자 `content`에만 달려 있고 인자가 캐시 키가 되므로, 글을 고치면 새 키가 된다. 같은 본문을 다시 하이라이팅할 이유가 없어 가장 긴 프로필(`max`)을 쓴다. 글 수정 반영 주기는 `getPost`의 60초가 정한다.
- `MarkdownRenderer`는 async 서버 컴포넌트라 단위 테스트하지 않는다. 렌더링 결과는 글 상세 페이지에서 시드 글로 확인한다.

### 프론트엔드 테스트
- Vitest로 `lib/`의 로직이 있는 순수 함수만 단위 테스트한다 (`date.ts`, `errors.ts`의 파싱 함수, `pagination.ts`, `slug.ts`).
  - `async` 서버 컴포넌트는 Vitest·Jest 모두 단위 테스트를 지원하지 않으므로 단위 테스트하지 않는다.
  - `server.ts`는 `server-only`가 테스트 환경에서 import 시 오류를 던지므로 단위 테스트하지 않는다 (실제 호출은 화면에서 확인).
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

