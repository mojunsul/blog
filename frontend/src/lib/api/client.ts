import { parseApiError } from "@/lib/api/errors";
import { readCookie } from "@/lib/cookie";
import { singleFlight } from "@/lib/single-flight";

const BASE_URL = "/api/v1";
const CSRF_COOKIE = "XSRF-TOKEN";
// 로그인의 401은 자격 증명 오류라 재발급 대상이 아니고, 재발급 자체는 아래 흐름이 직접 부른다.
const NO_REFRESH_PATHS = ["/auth/login", "/auth/refresh"];

interface RequestOptions {
  method?: string;
  body?: unknown;
}

async function ensureCsrfToken(): Promise<string> {
  const existing = readCookie(document.cookie, CSRF_COOKIE);
  if (existing) {
    return existing;
  }
  // 204 응답이라 apiFetch를 거치지 않고, 헤더 없이 보내 403으로 만들지 않도록 실패를 그대로 던진다.
  const response = await fetch(`${BASE_URL}/auth/csrf`);
  if (!response.ok) {
    throw parseApiError(response.status, response.headers.get("content-type"), await response.text());
  }
  const issued = readCookie(document.cookie, CSRF_COOKIE);
  if (!issued) {
    throw parseApiError(response.status, null, "");
  }
  return issued;
}

async function send(path: string, { method = "GET", body }: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET" && method !== "HEAD") {
    headers["X-XSRF-TOKEN"] = await ensureCsrfToken();
  }
  return fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

// 동시에 여러 요청이 401을 받아도 재발급은 한 번만 보낸다.
const refresh = singleFlight(async () => (await send("/auth/refresh", { method: "POST" })).ok);

function redirectToLogin() {
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  // 전체 새로고침으로 메모리에 남은 인증 상태를 버려야 해서 router 대신 쓴다.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`/admin/login?next=${next}`);
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options);

  if (response.status === 401 && !NO_REFRESH_PATHS.includes(path)) {
    const refreshed = await refresh();
    if (refreshed) {
      response = await send(path, options);
    }
    if (!refreshed || response.status === 401) {
      redirectToLogin();
    }
  }

  if (!response.ok) {
    throw parseApiError(response.status, response.headers.get("content-type"), await response.text());
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
