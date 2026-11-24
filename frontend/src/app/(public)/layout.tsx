import type { Metadata } from "next";
import Form from "next/form";
import Link from "next/link";

import { RSS_ALTERNATE_TYPES, RSS_PATH } from "@/lib/rss";
import { SITE_NAME } from "@/lib/site";

export const metadata: Metadata = { alternates: { types: RSS_ALTERNATE_TYPES } };

export default function PublicLayout({ children }: LayoutProps<"/">) {
  return (
    <>
      <header className="border-b border-zinc-200 dark:border-zinc-800">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-4">
          <Link href="/" className="text-lg font-semibold">
            {SITE_NAME}
          </Link>
          <Form action="/search" className="flex gap-2">
            <input
              type="search"
              name="q"
              maxLength={100}
              aria-label="검색어"
              placeholder="검색"
              className="rounded border border-zinc-300 px-2 py-1 dark:border-zinc-700"
            />
            <button type="submit" className="rounded border border-zinc-300 px-2 py-1 dark:border-zinc-700">
              검색
            </button>
          </Form>
        </div>
      </header>
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">{children}</main>
      <footer className="border-t border-zinc-200 dark:border-zinc-800">
        <div className="mx-auto max-w-3xl px-4 py-4">
          <a href={RSS_PATH}>RSS</a>
        </div>
      </footer>
    </>
  );
}
