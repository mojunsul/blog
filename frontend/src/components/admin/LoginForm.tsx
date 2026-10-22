"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { ApiError } from "@/lib/api/errors";
import { login } from "@/lib/api/auth";
import { parseNextParam } from "@/lib/next-path";
import { type LoginValues, loginSchema } from "@/schemas/auth";

const INVALID_CREDENTIALS_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";
const FAILURE_MESSAGE = "로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.";

export function LoginForm() {
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  async function onSubmit(values: LoginValues) {
    setFormError(null);
    try {
      await login(values);
    } catch (error) {
      const invalid = error instanceof ApiError && error.status === 401 && error.code === "INVALID_CREDENTIALS";
      setFormError(invalid ? INVALID_CREDENTIALS_MESSAGE : FAILURE_MESSAGE);
      return;
    }
    // useSearchParams는 Suspense 경계가 필요하고 제출 시점에만 읽으면 되므로 location에서 직접 읽는다.
    router.replace(parseNextParam(new URLSearchParams(window.location.search).get("next")));
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
      {formError && (
        <p role="alert" className="text-sm text-red-600">
          {formError}
        </p>
      )}
      <label className="flex flex-col gap-1 text-sm">
        아이디
        <input
          {...register("username")}
          type="text"
          autoComplete="username"
          className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-transparent"
        />
        {errors.username && <span className="text-red-600">{errors.username.message}</span>}
      </label>
      <label className="flex flex-col gap-1 text-sm">
        비밀번호
        <input
          {...register("password")}
          type="password"
          autoComplete="current-password"
          className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-transparent"
        />
        {errors.password && <span className="text-red-600">{errors.password.message}</span>}
      </label>
      <button
        type="submit"
        disabled={isSubmitting}
        className="rounded bg-zinc-900 px-3 py-2 text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
      >
        로그인
      </button>
    </form>
  );
}
