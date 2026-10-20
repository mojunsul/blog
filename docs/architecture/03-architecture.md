## 3. 전체 아키텍처

```
사용자 브라우저
     │ https://blog.도메인
     ▼
┌──────────── OCI 인스턴스 (Docker Compose) ────────────┐
│  Nginx (:80, :443) + Certbot                          │
│   ├ /api/*  → backend  (Spring Boot :8080)            │
│   └ 그 외   → frontend (Next.js :3000)                 │
│                                                       │
│  backend ──→ postgres (:5432, 외부 비공개)              │
│  frontend ─(서버 컴포넌트 fetch)→ http://backend:8080   │
│  backup (cron) ─ pg_dump → OCI Object Storage         │
└───────────────────────────────────────────────────────┘
```

**핵심 결정**
- API는 `api.` 서브도메인이 아니라 **같은 도메인의 `/api` 경로**로 연결한다.
  - same-origin이므로 CORS 설정이 필요 없다.
  - 인증 쿠키가 별도 도메인 설정 없이 전달된다.
- Nginx `proxy_pass http://backend:8080;` (끝에 `/` 없음) → `/api` 경로가 그대로 Spring에 전달된다.
- `/actuator`, `/swagger-ui`, `/v3/api-docs`는 `/api`로 시작하지 않으므로 외부에서 Spring에 도달하지 않는다. 헬스체크는 Docker 네트워크 내부(`http://backend:8080/actuator/health`)에서만 호출한다.
- PostgreSQL 포트는 외부에 열지 않는다.
- Spring은 프록시 뒤에서 동작하므로 `server.forward-headers-strategy: framework`를 설정한다.
- **모든 시간은 UTC로 저장**하고, API는 ISO-8601 + 오프셋 형식으로 응답한다. 표시용 시간대 변환(KST)은 프론트에서 한다.

---

