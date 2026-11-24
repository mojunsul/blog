import type { MetadataRoute } from "next";
import { connection } from "next/server";

import { getAllPublishedPosts } from "@/lib/api/posts";
import { getPublicCategories, getPublicTags } from "@/lib/api/taxonomy";
import { siteUrl } from "@/lib/site";
import { buildSitemap } from "@/lib/sitemap";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  await connection();
  const baseUrl = siteUrl().href;
  const [posts, categories, tags] = await Promise.all([
    getAllPublishedPosts(),
    getPublicCategories(),
    getPublicTags(),
  ]);
  return buildSitemap(baseUrl, posts, categories, tags);
}
