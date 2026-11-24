import type { MetadataRoute } from "next";
import { connection } from "next/server";

import { getSiteUrl } from "@/lib/site";

export default async function robots(): Promise<MetadataRoute.Robots> {
  await connection();
  return {
    rules: { userAgent: "*", allow: "/", disallow: ["/admin", "/api/"] },
    sitemap: new URL("/sitemap.xml", getSiteUrl()).href,
  };
}
