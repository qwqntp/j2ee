"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { signIn, signUp } from "@/lib/auth";
import { publishFlash } from "@/lib/flash";

type Props = { mode: "login" | "register" };

export function AuthForm({ mode }: Props) {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next") || "/tours";
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const isRegister = mode === "register";

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending(true);

    const data = new FormData(event.currentTarget);
    try {
      const session = isRegister
        ? await signUp({
            username: String(data.get("username") ?? ""),
            password: String(data.get("password") ?? ""),
            email: String(data.get("email") ?? ""),
          })
        : await signIn({
            username: String(data.get("username") ?? ""),
            password: String(data.get("password") ?? ""),
          });

      publishFlash(
        isRegister
          ? `Account created — welcome, ${session.account.username}!`
          : `Welcome back, ${session.account.username}!`,
      );
      router.push(next);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not reach the booking service.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="rounded-[4px] border border-line bg-white p-6 sm:p-8">
        <p className="eyebrow text-terracotta">{isRegister ? "Create account" : "Welcome back"}</p>
        <h1 className="mt-3 font-display text-3xl tracking-tight">
          {isRegister ? "Join the journey." : "Sign in to continue."}
        </h1>
        <p className="mt-2 text-sm leading-6 text-muted">
          {isRegister
            ? "An account lets you reserve seats and keep your bookings together."
            : "Use your account to reserve seats on a departure."}
        </p>

        <form onSubmit={onSubmit} className="mt-6 space-y-5">
          <div>
            <label htmlFor="username" className="label">
              Username
            </label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              required
              minLength={3}
              maxLength={50}
              className="field"
              placeholder="your-handle"
            />
          </div>

          {isRegister ? (
            <div>
              <label htmlFor="email" className="label">
                Email
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                maxLength={255}
                className="field"
                placeholder="you@example.com"
              />
            </div>
          ) : null}

          <div>
            <label htmlFor="password" className="label">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete={isRegister ? "new-password" : "current-password"}
              required
              minLength={isRegister ? 8 : undefined}
              maxLength={100}
              className="field"
              placeholder="At least 8 characters"
            />
          </div>

          {error ? (
            <p role="alert" className="rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-3 text-sm text-terracotta">
              {error}
            </p>
          ) : null}

          <button type="submit" disabled={pending} className="btn-primary w-full">
            {pending ? "Please wait…" : isRegister ? "Create account" : "Sign in"}
          </button>
        </form>

        <p className="mt-5 text-center text-sm text-muted">
          {isRegister ? "Already have an account?" : "New here?"}{" "}
          <Link
            href={`${isRegister ? "/login" : "/register"}?next=${encodeURIComponent(next)}`}
            className="font-medium text-forest hover:underline"
          >
            {isRegister ? "Sign in" : "Create an account"}
          </Link>
        </p>
      </div>
    </div>
  );
}
