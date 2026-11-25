import type { Metadata } from "next";

import PublicShell from "@/components/layout/PublicShell";
import { RSS_ALTERNATE_TYPES } from "@/lib/rss";

export const metadata: Metadata = { alternates: { types: RSS_ALTERNATE_TYPES } };

export default function PublicLayout({ children }: LayoutProps<"/">) {
  return <PublicShell>{children}</PublicShell>;
}
