## 1. 기술 스택

### 프론트엔드
| 기술 | 용도 |
|---|---|
| Next.js 16 (App Router) + TypeScript | 서버 렌더링 + 캐시 기반 블로그 화면, SEO |
| Tailwind CSS | 스타일링 |
| react-markdown + remark-gfm + rehype-slug + Shiki(`@shikijs/rehype`) | Markdown 렌더링, 코드 하이라이팅 |
| `@tailwindcss/typography` | Markdown 본문 스타일 (`prose`) |
| react-hook-form + Zod (`@hookform/resolvers`) | 관리자 폼 처리·검증 |
| openapi-typescript | 백엔드 OpenAPI 명세에서 TS 타입 자동 생성 |
| Vitest | `lib/`의 순수 함수 단위 테스트 |

### 백엔드
| 기술 | 용도 |
|---|---|
| Java 21 + Spring Boot (Initializr 최신 안정 버전) + Gradle | API 서버 |
| Spring Web, Validation | REST API, 입력 검증 |
| Spring Data JPA + QueryDSL | 기본 CRUD + 동적 검색 쿼리 |
| Spring Security + JWT (HttpOnly 쿠키) + CSRF 보호 | 관리자 인증·인가 |
| Flyway | DB 스키마 마이그레이션 |
| springdoc-openapi | Swagger UI, OpenAPI 명세 (로컬 전용) |
| Spring Actuator | 헬스체크 (`health`만 노출) |
| JUnit5 + Testcontainers | 실제 PostgreSQL로 통합 테스트 |

### 데이터베이스
- PostgreSQL 18 (로컬·테스트·운영 동일 버전, 검색은 초기에 내장 기능 사용, Redis는 고도화 단계에서 도입)

### 인프라
| 기술 | 용도 |
|---|---|
| Docker + Docker Compose | 로컬/서버 실행 환경 통일 |
| Nginx + Certbot | 리버스 프록시, HTTPS, rate limiting (학습 목적으로 Caddy 대신 선택) |
| GitHub Actions + GHCR | CI/CD, 이미지 레지스트리 |
| Oracle Cloud (ARM 인스턴스) | 서버 (이미지는 `linux/arm64`로 빌드) |
| OCI Object Storage | DB 백업 저장, (M6) 이미지 업로드 |
| Cloudflare DNS | 도메인·서브도메인 관리 |

### 부가 기능
- Giscus(댓글), Umami(방문자 분석), Uptime Kuma(모니터링), sitemap/RSS

---

