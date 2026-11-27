import type { Metadata } from "next";
import { Suspense } from "react";

import PublicShell from "@/components/layout/PublicShell";
import UmamiScript from "@/components/layout/UmamiScript";
import { RSS_ALTERNATE_TYPES } from "@/lib/rss";

export const metadata: Metadata = { alternates: { types: RSS_ALTERNATE_TYPES } };

export default function PublicLayout({ children }: LayoutProps<"/">) {
  return (
    <>
      <PublicShell>{children}</PublicShell>
      <Suspense fallback={null}>
        <UmamiScript />
      </Suspense>
    </>
  );
}
