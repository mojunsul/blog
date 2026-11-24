import type { MetadataRoute } from "next";

import { categoryHref, tagHref } from "@/lib/taxonomy";
import type { components } from "@/types/api";

type Post = Pick<components["schemas"]["PostSummaryResponse"], "slug" | "updatedAt">;
type Category = Pick<components["schemas"]["CategoryResponse"], "slug" | "postCount">;
type Tag = Pick<components["schemas"]["TagResponse"], "name">;

export function buildSitemap(
  siteUrl: string,
  posts: Post[],
  categories: Category[],
  tags: Tag[],
): MetadataRoute.Sitemap {
  const abs = (path: string) => new URL(path, siteUrl).href;
  return [
    { url: abs("/") },
    ...posts.map((post) => ({ url: abs(`/posts/${post.slug}`), lastModified: post.updatedAt })),
    ...categories.filter((c) => c.postCount > 0).map((c) => ({ url: abs(categoryHref(c.slug, 1)) })),
    ...tags.map((t) => ({ url: abs(tagHref(t.name, 1)) })),
  ];
}
