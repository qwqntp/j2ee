import type { Metadata } from "next";
import { Suspense } from "react";
import { ResetPasswordForm } from "@/components/reset-password-form";

export const metadata: Metadata = {
  title: "Reset password",
  description: "Choose a new password for your tour account.",
};

export default function ResetPasswordPage() {
  return (
    <section className="shell py-12 sm:py-16">
      <Suspense fallback={<p className="text-center text-sm text-muted">Loading reset link…</p>}>
        <ResetPasswordForm />
      </Suspense>
    </section>
  );
}
