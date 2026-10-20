## 4. 저장소 전체 구조

```
blog/
├ backend/                 # Spring Boot
├ frontend/                # Next.js
├ infra/
│  ├ docker-compose.yml          # 로컬용 (postgres만)
│  ├ docker-compose.prod.yml     # 서버용 (nginx, certbot, frontend, backend, postgres, backup)
│  ├ .env.example                # 필요한 환경변수 목록 (값은 비움)
│  ├ nginx/
│  │  ├ nginx.conf               # 공통 설정 (gzip, 로그 형식, rate limit zone)
│  │  └ conf.d/
│  │     └ blog.conf             # blog.도메인 server 블록
│  └ backup/
│     └ backup.sh                # pg_dump → 압축 → Object Storage 업로드
├ docs/
│  ├ architecture.md             # 설계 문서 목차
│  ├ architecture/               # 설계 문서 장별 파일 (NN-*.md)
│  ├ changelog.md                # 설계 문서 변경 이력
│  └ verification.md             # 검증 세션 규칙 (CLAUDE.md에서 분리)
├ .github/
│  └ workflows/
│     ├ backend-ci.yml     # backend/** 변경 시: 테스트, 빌드
│     ├ frontend-ci.yml    # frontend/** 변경 시: lint, typecheck, build
│     └ deploy.yml         # main 머지 시: arm64 이미지 빌드 → GHCR → SSH 배포
├ CLAUDE.md
├ .gitignore
└ README.md
```

---

