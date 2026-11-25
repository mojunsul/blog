import Link from "next/link";

import PublicShell from "@/components/layout/PublicShell";

export default function NotFound() {
  return (
    <PublicShell>
      <p>페이지를 찾을 수 없습니다.</p>
      <Link href="/" className="mt-4 inline-block underline">
        홈으로
      </Link>
    </PublicShell>
  );
}
