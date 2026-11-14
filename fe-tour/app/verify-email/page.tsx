import type { Metadata } from "next";
import { Suspense } from "react";
import { VerifyEmail } from "@/components/verify-email";

export const metadata: Metadata = {
  title: "Verify email",
  description: "Confirm the email address linked to your account.",
};

export default function VerifyEmailPage() {
  return (
    <section className="shell py-14 sm:py-20">
      <div className="mx-auto max-w-2xl text-center">
        <p className="eyebrow text-terracotta">Account</p>
        <h1 className="mt-3 font-display text-4xl leading-tight tracking-tight sm:text-5xl">
          Email verification.
        </h1>
        <p className="mx-auto mt-4 max-w-xl text-sm leading-6 text-muted">
          The link in your email brings you here. We will confirm the address on your account.
        </p>
      </div>

      <div className="mt-10">
        <Suspense
          fallback={
            <p className="mx-auto max-w-xl text-center text-sm text-muted">
              Loading your verification link…
            </p>
          }
        >
          <VerifyEmail />
        </Suspense>
      </div>
    </section>
  );
}
