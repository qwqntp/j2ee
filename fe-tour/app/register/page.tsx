import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/components/auth-form";

export const metadata: Metadata = {
  title: "Create account",
  description: "Create an account to reserve seats and keep your bookings together.",
};

export default function RegisterPage() {
  return (
    <section className="shell py-12 sm:py-16">
      <Suspense fallback={null}>
        <AuthForm mode="register" />
      </Suspense>
    </section>
  );
}
