"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";

import { createCategory, updateCategory } from "@/lib/api/admin-categories";
import { ApiError } from "@/lib/api/errors";
import { type CategoryValues, categorySchema } from "@/schemas/category";
import type { components } from "@/types/api";

type Category = components["schemas"]["CategoryResponse"];

const EMPTY_VALUES: CategoryValues = { name: "", slug: "" };
const FORM_FIELDS = Object.keys(EMPTY_VALUES) as (keyof CategoryValues)[];
const CATEGORY_DELETED_MESSAGE = "카테고리가 삭제되어 저장하지 못했습니다.";
const SAVE_FAILURE_MESSAGE = "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const INPUT_CLASS = "rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-transparent";

function hasCode(error: unknown, status: number, code: string): error is ApiError {
  return error instanceof ApiError && error.status === status && error.code === code;
}

function isFormField(field: string): field is keyof CategoryValues {
  return (FORM_FIELDS as string[]).includes(field);
}

// 모드가 바뀔 때마다 AdminCategoryManager가 key로 다시 마운트해, 입력값·오류를 따로 초기화하지 않는다.
export function CategoryForm({
  editing,
  disabled,
  onSubmittingChange,
  onSaved,
  onCategoryDeleted,
  onCancel,
}: {
  editing: Category | null;
  disabled: boolean;
  onSubmittingChange: (submitting: boolean) => void;
  onSaved: () => void;
  onCategoryDeleted: () => void;
  onCancel: () => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<CategoryValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: editing ? { name: editing.name, slug: editing.slug } : EMPTY_VALUES,
  });
  const buttonsDisabled = disabled || isSubmitting;

  useEffect(() => {
    // 폼이 표 위에 있어 키보드 사용자가 찾아가지 않아도 되게 한다.
    if (editing) setFocus("name");
  }, [editing, setFocus]);

  async function onValid(values: CategoryValues) {
    onSubmittingChange(true);
    try {
      if (editing) {
        await updateCategory(editing.id, values);
      } else {
        await createCategory(values);
      }
    } catch (error) {
      handleSaveError(error);
      return;
    } finally {
      onSubmittingChange(false);
    }
    onSaved();
  }

  function handleSaveError(error: unknown) {
    if (error instanceof ApiError && error.status === 401) return;
    if (hasCode(error, 400, "INVALID_INPUT")) {
      const shown = new Set<string>();
      for (const { field, reason } of error.errors) {
        if (!isFormField(field) || shown.has(field)) continue;
        shown.add(field);
        setError(field, { type: "server", message: reason });
      }
      if (shown.size > 0) return;
    }
    if (hasCode(error, 409, "DUPLICATE_SLUG")) {
      setError("slug", { type: "server", message: error.detail });
    } else if (hasCode(error, 404, "CATEGORY_NOT_FOUND")) {
      // detail("카테고리를 찾을 수 없습니다.")로는 저장하지 못했다는 결과가 드러나지 않아 고정 문구를 쓴다.
      setFormError(CATEGORY_DELETED_MESSAGE);
      onCategoryDeleted();
    } else {
      setFormError(SAVE_FAILURE_MESSAGE);
    }
  }

  return (
    <form
      onSubmit={(event) => {
        setFormError(null);
        return handleSubmit(onValid)(event);
      }}
      className="mb-8 flex flex-col gap-4"
      noValidate
    >
      <h2 className="font-semibold">{editing ? "카테고리 수정" : "새 카테고리"}</h2>
      <label className="flex flex-col gap-1 text-sm">
        이름
        <input {...register("name")} type="text" className={INPUT_CLASS} />
        {errors.name && <span className="text-red-600">{errors.name.message}</span>}
      </label>
      <label className="flex flex-col gap-1 text-sm">
        slug
        <input {...register("slug")} type="text" className={INPUT_CLASS} />
        <span className="text-zinc-500">영문 소문자·숫자·하이픈만 쓸 수 있습니다.</span>
        {errors.slug && <span className="text-red-600">{errors.slug.message}</span>}
      </label>
      {formError && (
        <p role="alert" className="text-sm text-red-600">
          {formError}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={buttonsDisabled}
          className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
        >
          {editing ? "저장" : "추가"}
        </button>
        {editing && (
          <button
            type="button"
            onClick={onCancel}
            disabled={buttonsDisabled}
            className="rounded border border-zinc-300 px-4 py-2 disabled:opacity-50 dark:border-zinc-700"
          >
            취소
          </button>
        )}
      </div>
    </form>
  );
}
