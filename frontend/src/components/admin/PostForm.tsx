"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { type FieldErrors, useForm } from "react-hook-form";

import { MarkdownPreview } from "@/components/admin/MarkdownPreview";
import { getCategories } from "@/lib/api/admin-categories";
import { createPost, getAdminPost, updatePost } from "@/lib/api/admin-posts";
import { ApiError } from "@/lib/api/errors";
import { type PostValues, postSchema } from "@/schemas/post";
import type { components } from "@/types/api";

type AdminPostDetail = components["schemas"]["AdminPostDetailResponse"];
type Category = components["schemas"]["CategoryResponse"];
type PostRequest = components["schemas"]["PostRequest"];

type LoadResult = { categories: Category[]; post: AdminPostDetail | null } | { failure: "notFound" | "failed" };

const DEFAULT_VALUES: PostValues = {
  title: "",
  slug: "",
  categoryId: "",
  status: "DRAFT",
  summary: "",
  thumbnailUrl: "",
  content: "",
};
const FORM_FIELDS = Object.keys(DEFAULT_VALUES) as (keyof PostValues)[];
const LOAD_FAILURE_MESSAGE = "글을 불러오지 못했습니다. 새로고침해 주세요.";
const POST_DELETED_MESSAGE = "글이 삭제되어 저장하지 못했습니다.";
const SAVE_FAILURE_MESSAGE = "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const INPUT_CLASS = "rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-transparent";

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

function hasCode(error: unknown, status: number, code: string): error is ApiError {
  return error instanceof ApiError && error.status === status && error.code === code;
}

function isFormField(field: string): field is keyof PostValues {
  return (FORM_FIELDS as string[]).includes(field);
}

function toValues(post: AdminPostDetail, categories: Category[]): PostValues {
  // 두 요청 사이에 카테고리가 지워졌으면 맞는 option이 없어 빈 칸으로 보이고 저장 시 404가 되므로 "카테고리 없음"으로 둔다.
  const categoryExists = categories.some((category) => category.id === post.categoryId);
  return {
    title: post.title,
    slug: post.slug,
    categoryId: categoryExists ? String(post.categoryId) : "",
    status: post.status,
    summary: post.summary ?? "",
    thumbnailUrl: post.thumbnailUrl ?? "",
    content: post.content,
  };
}

function toRequest({ categoryId, ...values }: PostValues): PostRequest {
  return categoryId === "" ? values : { ...values, categoryId: Number(categoryId) };
}

export function PostForm({ id }: { id?: number }) {
  const router = useRouter();
  const [loadResult, setLoadResult] = useState<LoadResult | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [previewContent, setPreviewContent] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const focusContentAfterWrite = useRef(false);
  const {
    register,
    handleSubmit,
    reset,
    getValues,
    setError,
    setFocus,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<PostValues>({ resolver: zodResolver(postSchema), defaultValues: DEFAULT_VALUES });

  useEffect(() => {
    let ignored = false;
    Promise.allSettled([getCategories(), id === undefined ? null : getAdminPost(id)]).then(([categories, post]) => {
      if (ignored) return;
      const failures = [categories, post].flatMap((result) => (result.status === "rejected" ? [result.reason] : []));
      // 401은 client.ts가 로그인 화면으로 이동시키는 중이라 "불러오는 중..."을 유지한다.
      if (failures.some(isUnauthorized)) return;
      if (post.status === "rejected" && hasCode(post.reason, 404, "POST_NOT_FOUND")) {
        setLoadResult({ failure: "notFound" });
        return;
      }
      if (categories.status === "rejected" || post.status === "rejected") {
        setLoadResult({ failure: "failed" });
        return;
      }
      reset(post.value ? toValues(post.value, categories.value) : DEFAULT_VALUES);
      setLoadResult({ categories: categories.value, post: post.value });
    });
    return () => {
      ignored = true;
    };
  }, [id, reset]);

  useEffect(() => {
    if (!isDirty) return;
    // App Router에는 클라이언트 이동을 막는 API가 없어 새로고침·탭 닫기·전체 이동만 경고한다.
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty]);

  useEffect(() => {
    // 미리보기 중 숨은 textarea는 포커스를 받지 못하므로 "작성"으로 바뀐 뒤에 옮긴다.
    if (!previewing && focusContentAfterWrite.current) {
      focusContentAfterWrite.current = false;
      setFocus("content");
    }
  }, [previewing, setFocus]);

  if (!loadResult) return <p>불러오는 중...</p>;
  if ("failure" in loadResult) {
    if (loadResult.failure === "failed") return <p>{LOAD_FAILURE_MESSAGE}</p>;
    return (
      <div className="flex flex-col items-start gap-2">
        <p>글을 찾을 수 없습니다.</p>
        <Link href="/admin/posts">글 목록으로</Link>
      </div>
    );
  }
  const { categories, post: loadedPost } = loadResult;

  function onInvalid(fieldErrors: FieldErrors<PostValues>) {
    if (!fieldErrors.content || !previewing) return;
    // 본문은 마지막 입력 항목이라, 오류가 본문뿐일 때만 첫 오류 필드가 본문이다.
    focusContentAfterWrite.current = Object.keys(fieldErrors).length === 1;
    setPreviewing(false);
  }

  async function onValid(values: PostValues) {
    if (
      id !== undefined &&
      loadedPost?.status === "PUBLISHED" &&
      values.slug !== loadedPost.slug &&
      !window.confirm(`slug를 바꾸면 기존 주소 /posts/${loadedPost.slug}는 더 이상 열리지 않습니다. 저장할까요?`)
    ) {
      return;
    }
    try {
      if (id === undefined) {
        await createPost(toRequest(values));
      } else {
        await updatePost(id, toRequest(values));
      }
    } catch (error) {
      handleSaveError(error);
      return;
    }
    router.push("/admin/posts");
  }

  function handleSaveError(error: unknown) {
    if (isUnauthorized(error)) return;
    if (hasCode(error, 400, "INVALID_INPUT")) {
      const shown = new Set<string>();
      for (const { field, reason } of error.errors) {
        if (!isFormField(field) || shown.has(field)) continue;
        shown.add(field);
        setError(field, { type: "server", message: reason });
      }
      if (shown.has("content")) setPreviewing(false);
      if (shown.size > 0) return;
    }
    if (hasCode(error, 409, "DUPLICATE_SLUG")) {
      setError("slug", { type: "server", message: error.detail });
    } else if (hasCode(error, 404, "CATEGORY_NOT_FOUND")) {
      setError("categoryId", { type: "server", message: error.detail });
    } else if (hasCode(error, 404, "POST_NOT_FOUND")) {
      // detail("글을 찾을 수 없습니다.")로는 저장하지 못했다는 결과가 드러나지 않아 고정 문구를 쓴다.
      setFormError(POST_DELETED_MESSAGE);
    } else {
      setFormError(SAVE_FAILURE_MESSAGE);
    }
  }

  function showPreview() {
    setPreviewContent(getValues("content"));
    setPreviewing(true);
  }

  return (
    <form
      onSubmit={(event) => {
        setFormError(null);
        return handleSubmit(onValid, onInvalid)(event);
      }}
      className="flex flex-col gap-4"
      noValidate
    >
      <label className="flex flex-col gap-1 text-sm">
        제목
        <input {...register("title")} type="text" className={INPUT_CLASS} />
        {errors.title && <span className="text-red-600">{errors.title.message}</span>}
      </label>
      <label className="flex flex-col gap-1 text-sm">
        slug
        <input {...register("slug")} type="text" className={INPUT_CLASS} />
        <span className="text-zinc-500">영문 소문자·숫자·하이픈만 쓸 수 있습니다. 공개 주소 /posts/{"{slug}"}가 됩니다.</span>
        {errors.slug && <span className="text-red-600">{errors.slug.message}</span>}
      </label>
      <label className="flex flex-col gap-1 text-sm">
        카테고리
        <select {...register("categoryId")} className={INPUT_CLASS}>
          <option value="">카테고리 없음</option>
          {/* name은 중복될 수 있어 slug를 함께 보인다. */}
          {categories.map((category) => (
            <option key={category.id} value={String(category.id)}>
              {`${category.name} (${category.slug})`}
            </option>
          ))}
        </select>
        {errors.categoryId && <span className="text-red-600">{errors.categoryId.message}</span>}
      </label>
      <fieldset className="flex flex-col gap-1 text-sm">
        <legend className="mb-1">상태</legend>
        <div className="flex gap-4">
          <label className="flex items-center gap-1">
            <input {...register("status")} type="radio" value="DRAFT" />
            작성 중
          </label>
          <label className="flex items-center gap-1">
            <input {...register("status")} type="radio" value="PUBLISHED" />
            발행됨
          </label>
        </div>
        {errors.status && <span className="text-red-600">{errors.status.message}</span>}
      </fieldset>
      <label className="flex flex-col gap-1 text-sm">
        요약
        <textarea {...register("summary")} rows={2} className={INPUT_CLASS} />
        {errors.summary && <span className="text-red-600">{errors.summary.message}</span>}
      </label>
      <label className="flex flex-col gap-1 text-sm">
        썸네일 URL
        <input {...register("thumbnailUrl")} type="text" className={INPUT_CLASS} />
        {errors.thumbnailUrl && <span className="text-red-600">{errors.thumbnailUrl.message}</span>}
      </label>
      <div className="flex flex-col gap-1 text-sm">
        <div className="flex items-center gap-2">
          <label htmlFor="post-content" className="mr-auto">
            본문
          </label>
          <button
            type="button"
            onClick={() => setPreviewing(false)}
            aria-pressed={!previewing}
            className="aria-pressed:font-semibold aria-pressed:underline"
          >
            작성
          </button>
          <button
            type="button"
            onClick={showPreview}
            aria-pressed={previewing}
            className="aria-pressed:font-semibold aria-pressed:underline"
          >
            미리보기
          </button>
        </div>
        <textarea {...register("content")} id="post-content" rows={20} hidden={previewing} className={`${INPUT_CLASS} font-mono`} />
        {previewing && (
          <div className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
            <MarkdownPreview content={previewContent} />
          </div>
        )}
        {errors.content && <span className="text-red-600">{errors.content.message}</span>}
      </div>
      {formError && (
        <p role="alert" className="text-sm text-red-600">
          {formError}
        </p>
      )}
      <button
        type="submit"
        disabled={isSubmitting}
        className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
      >
        저장
      </button>
    </form>
  );
}
