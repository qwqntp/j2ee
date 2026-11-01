import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/components/auth-form";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Sign in to reserve seats and manage your journeys.",
};

export default function LoginPage() {
  return (
    <section className="shell py-12 sm:py-16">
      <Suspense fallback={null}>
        <AuthForm mode="login" />
      </Suspense>
    </section>
  );
}
