import type { Metadata } from "next";
import { ForgotPasswordForm } from "@/components/forgot-password-form";

export const metadata: Metadata = {
  title: "Forgot password",
  description: "Request a link to reset your tour account password.",
};

export default function ForgotPasswordPage() {
  return (
    <section className="shell py-12 sm:py-16">
      <ForgotPasswordForm />
    </section>
  );
}
