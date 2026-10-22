const DEFAULT_PATH = "/admin";

// 외부 주소로 보내는 열린 리다이렉트를 막기 위해 같은 출처의 /admin 아래 경로만 허용한다.
export function parseNextParam(raw: string | null): string {
  if (raw === null) {
    return DEFAULT_PATH;
  }
  const underAdmin = raw === "/admin" || raw.startsWith("/admin/") || raw.startsWith("/admin?");
  // 로그인 화면으로 다시 보내면 로그인 직후 같은 화면에 머무는 반복 이동이 된다.
  const isLogin = raw.startsWith("/admin/login");
  const hasUnsafeChar = /[\\\u0000-\u001f\u007f]/.test(raw);
  return underAdmin && !isLogin && !hasUnsafeChar ? raw : DEFAULT_PATH;
}
