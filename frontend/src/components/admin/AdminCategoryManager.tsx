"use client";

import { useEffect, useState } from "react";

import { CategoryForm } from "@/components/admin/CategoryForm";
import { deleteCategory, getCategories } from "@/lib/api/admin-categories";
import { ApiError } from "@/lib/api/errors";
import type { components } from "@/types/api";

type Category = components["schemas"]["CategoryResponse"];

type LoadResult = { key: number; categories: Category[] } | { key: number; failed: true };

const LOAD_FAILURE_MESSAGE = "카테고리 목록을 불러오지 못했습니다. 새로고침해 주세요.";
const DELETE_FAILURE_MESSAGE = "삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.";

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

function isCategoryNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404 && error.code === "CATEGORY_NOT_FOUND";
}

export function AdminCategoryManager() {
  const [reloadCount, setReloadCount] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const [editing, setEditing] = useState<Category | null>(null);
  const [formKey, setFormKey] = useState(0);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  // effect에서 동기로 "불러오는 중" 상태를 넣지 않고, 결과의 요청 키가 지금 요청과 다르면 불러오는 중으로 본다.
  const loaded = result?.key === reloadCount ? result : null;
  const busy = saving || deleting;

  useEffect(() => {
    let ignored = false;
    getCategories().then(
      (categories) => {
        if (!ignored) setResult({ key: reloadCount, categories });
      },
      (error) => {
        // 401은 client.ts가 로그인 화면으로 이동시키는 중이라 오류 문구 없이 "불러오는 중..."을 유지한다.
        if (!ignored && !isUnauthorized(error)) setResult({ key: reloadCount, failed: true });
      },
    );
    return () => {
      ignored = true;
    };
  }, [reloadCount]);

  function reload() {
    setReloadCount((count) => count + 1);
  }

  // 같은 행의 "수정"을 다시 눌러도 값·오류가 초기화되도록 모드를 바꿀 때마다 폼을 다시 마운트한다.
  function changeMode(category: Category | null) {
    setEditing(category);
    setFormKey((key) => key + 1);
  }

  async function handleDelete(category: Category) {
    if (
      !window.confirm(
        `"${category.name}" 카테고리를 삭제할까요? 이 카테고리의 글(작성 중인 글 포함)은 삭제되지 않고 카테고리 없음이 됩니다.`,
      )
    ) {
      return;
    }
    setDeleting(true);
    try {
      await deleteCategory(category.id);
    } catch (error) {
      if (isUnauthorized(error)) return;
      // CATEGORY_NOT_FOUND는 다른 탭 등에서 이미 지운 경우라 바라던 상태이므로 문구 없이 목록만 갱신한다.
      if (!isCategoryNotFound(error)) {
        setDeleting(false);
        window.alert(DELETE_FAILURE_MESSAGE);
        return;
      }
    }
    setDeleting(false);
    if (editing?.id === category.id) changeMode(null);
    reload();
  }

  return (
    <>
      <CategoryForm
        key={formKey}
        editing={editing}
        disabled={deleting}
        onSubmittingChange={setSaving}
        onSaved={() => {
          changeMode(null);
          reload();
        }}
        onCategoryDeleted={reload}
        onCancel={() => changeMode(null)}
      />
      {renderList()}
    </>
  );

  function renderList() {
    if (!loaded) return <p>불러오는 중...</p>;
    if ("failed" in loaded) return <p>{LOAD_FAILURE_MESSAGE}</p>;
    if (loaded.categories.length === 0) return <p>카테고리가 없습니다.</p>;

    return (
      <table className="w-full text-left text-sm">
        <thead className="border-b border-zinc-200 dark:border-zinc-800">
          <tr>
            <th scope="col" className="py-2">이름</th>
            <th scope="col">slug</th>
            {/* postCount는 PUBLISHED만 세어 작성 중 글이 빠진다. */}
            <th scope="col">발행 글 수</th>
            <th scope="col">
              <span className="sr-only">수정</span>
            </th>
            <th scope="col">
              <span className="sr-only">삭제</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
          {loaded.categories.map((category) => (
            <tr key={category.id}>
              <td className="py-2">{category.name}</td>
              <td>{category.slug}</td>
              <td>{category.postCount}</td>
              <td>
                <button type="button" onClick={() => changeMode(category)} disabled={busy} className="disabled:opacity-50">
                  수정
                </button>
              </td>
              <td>
                <button
                  type="button"
                  onClick={() => handleDelete(category)}
                  disabled={busy}
                  className="text-red-600 disabled:opacity-50"
                >
                  삭제
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
}
