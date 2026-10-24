# 블로그 프로젝트 설계 문서 목차

> 설계 기준 문서의 목차. 본문은 `docs/architecture/`의 장별 파일에 있다.
> 이 문서와 구현이 충돌하면 해당 장 파일을 먼저 수정한 뒤 구현한다.
> 이슈에서 "5장"이라고 부르면 `docs/architecture/05-backend.md`다. 변경 이력은 `docs/changelog.md`.

| 장 | 제목 | 파일 | 요약 |
|---|---|---|---|
| 0 | 프로젝트 개요 | `docs/architecture/00-overview.md` | 목적, 작성자, 글 작성 방식, 댓글(Giscus), 배포 환경 |
| 1 | 기술 스택 | `docs/architecture/01-tech-stack.md` | 프론트엔드·백엔드·데이터베이스·인프라·부가 기능의 기술 선택 |
| 2 | 저장소 만들기 | `docs/architecture/02-repo-setup.md` | 저장소 방식(모노레포), 생성 절차, 루트 .gitignore 핵심 항목, 브랜치·커밋 규칙(GitHub Flow) |
| 3 | 전체 아키텍처 | `docs/architecture/03-architecture.md` | 요청 흐름 다이어그램, 핵심 결정(같은 도메인 `/api`, Nginx 프록시, 외부 비노출 경로, 프록시 헤더, UTC 시간) |
| 4 | 저장소 전체 구조 | `docs/architecture/04-repo-structure.md` | 모노레포 전체 디렉터리 구조 |
| 5 | 백엔드 구조 | `docs/architecture/05-backend.md` | 도메인 단위 패키지·계층 구조, 백엔드 규칙(로컬 시드 데이터, 테스트 프로필), 로깅(요청 추적 ID), 관리자 계정 생성 |
| 6 | DB 설계 | `docs/architecture/06-db.md` | 테이블 정의, 인덱스, FK 삭제 정책, 데이터 규칙(공개 조건, 발행 시각, 삭제, slug·태그 규칙, 조회수) |
| 7 | API 공통 규칙 | `docs/architecture/07-api-conventions.md` | base path, 형식, 응답 래퍼, 필드 이름, 시간, 페이지네이션, 식별자, 수정(PUT), 상태 코드 |
| 8 | API 명세 | `docs/architecture/08-api-spec.md` | 공개 API, 조회수 API(M6), 인증 API, 관리자 API(글·카테고리) 명세와 검증 규칙 |
| 9 | 에러 응답 형식 (ProblemDetail, RFC 9457) | `docs/architecture/09-errors.md` | ProblemDetail 확장 형식, ErrorCode 목록, ProblemDetail 생성 코드 공유, ProblemDetail이 아닌 응답 |
| 10 | 인증·보안 설계 | `docs/architecture/10-auth-security.md` | 토큰·쿠키, CSRF, 인증 흐름, 그 외 보안 규칙, Security 필터 설정, JWT와 로그인(Access·Refresh 토큰) |
| 11 | 프론트엔드 구조 | `docs/architecture/11-frontend.md` | App Router 규칙, 렌더링 전략, 빌드 시 API 의존 제거, API 주소, 에러 처리, 날짜 표시, 글 목록·상세 페이지, Markdown 렌더링, 테스트, 스크립트 |
| 12 | 인프라와 배포 | `docs/architecture/12-infra.md` | 로컬 개발, 서버(OCI·로그 용량 제한), HTTPS, Nginx 설정, DB 백업, CI/CD, Docker 이미지 |
| 13 | 마일스톤 | `docs/architecture/13-milestones.md` | M0~M6 단계별 내용·완료 기준과 진행 원칙 |
| 14 | 변경 이력 | `docs/changelog.md` | 문서 변경 이력 (장별 파일이 아닌 별도 파일) |
