// M1 임시 타입: M2에서 openapi-typescript 생성 타입(api.d.ts)으로 교체하고 이 파일은 삭제한다.

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface PostCategory {
  name: string;
  slug: string;
}

export interface PostSummary {
  id: number;
  title: string;
  slug: string;
  summary: string | null;
  thumbnailUrl: string | null;
  category: PostCategory | null;
  tags: string[];
  publishedAt: string;
}

export interface PostDetail extends PostSummary {
  content: string;
  updatedAt: string;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  postCount: number;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  errors: { field: string; reason: string }[];
}
