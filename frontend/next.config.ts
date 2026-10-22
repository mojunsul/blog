import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  // 운영은 Nginx가 /api를 백엔드로 보낸다. rewrites는 빌드 때 고정되어 실행 시점 환경변수를 읽지 못하므로 개발에서만 둔다.
  async rewrites() {
    if (process.env.NODE_ENV !== "development") {
      return [];
    }
    const apiUrl = process.env.API_INTERNAL_URL;
    if (!apiUrl) {
      throw new Error("API_INTERNAL_URL이 설정되지 않았습니다. frontend/.env.example을 .env.local로 복사하세요.");
    }
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
