import Link from "next/link";

import { tagHref } from "@/lib/taxonomy";

export function TagBadge({ name, postCount }: { name: string; postCount?: number }) {
  return (
    <Link href={tagHref(name, 1)} className="hover:underline">
      #{name}
      {postCount !== undefined && ` (${postCount})`}
    </Link>
  );
}
