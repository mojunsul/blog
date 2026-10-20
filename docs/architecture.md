# 블로그 프로젝트 설계 문서 목차

> 설계 기준 문서의 목차. 본문은 `docs/architecture/`의 장별 파일에 있다.
> 이 문서와 구현이 충돌하면 해당 장 파일을 먼저 수정한 뒤 구현한다.
> 이슈에서 "5장"이라고 부르면 `docs/architecture/05-backend.md`다. 변경 이력은 `docs/changelog.md`.

| 장 | 제목 | 파일 | 요약 |
|---|---|---|---|
| 0 | 프로젝트 개요 | `docs/architecture/00-overview.md` | 목적, 작성자, 글 작성 방식, 배포 환경 |
| 1 | 기술 스택 | `docs/architecture/01-tech-stack.md` | 백엔드·프론트엔드·인프라 기술 선택 |
| 2 | 저장소 만들기 | `docs/architecture/02-repo-setup.md` | 저장소 생성과 초기 설정 절차 |
| 3 | 전체 아키텍처 | `docs/architecture/03-architecture.md` | 요청 흐름 다이어그램 |
| 4 | 저장소 전체 구조 | `docs/architecture/04-repo-structure.md` | 모노레포 디렉터리 구조 |
| 5 | 백엔드 구조 | `docs/architecture/05-backend.md` | 백엔드 패키지·계층 구조와 규칙 |
| 6 | DB 설계 | `docs/architecture/06-db.md` | 테이블, 인덱스, FK 삭제 정책 |
| 7 | API 공통 규칙 | `docs/architecture/07-api-conventions.md` | base path, 상태 코드 등 API 공통 규칙 |
| 8 | API 명세 | `docs/architecture/08-api-spec.md` | 엔드포인트별 요청·응답·상태 코드 |
| 9 | 에러 응답 형식 (ProblemDetail, RFC 9457) | `docs/architecture/09-errors.md` | 에러 응답 형식과 ErrorCode 목록 |
| 10 | 인증·보안 설계 | `docs/architecture/10-auth-security.md` | 토큰·쿠키, CSRF, 인증 흐름, 보안 규칙, Security 필터, JWT·로그인 |
| 11 | 프론트엔드 구조 | `docs/architecture/11-frontend.md` | 렌더링 전략, 빌드·API 주소, 에러·날짜 처리, 화면, Markdown, 테스트 |
| 12 | 인프라와 배포 | `docs/architecture/12-infra.md` | 로컬 개발, 서버(OCI), HTTPS, Nginx, DB 백업, CI/CD |
| 13 | 마일스톤 | `docs/architecture/13-milestones.md` | M0~M6 마일스톤과 진행 원칙 |
| 14 | 변경 이력 | `docs/changelog.md` | 문서 변경 이력 (장별 파일이 아닌 별도 파일) |
