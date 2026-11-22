import type { Metadata } from "next";
import { Suspense } from "react";
import { AccountProfile } from "@/components/account-profile";
import { ProfileSkeleton } from "@/components/skeleton";

export const metadata: Metadata = {
  title: "Profile",
  description: "Your account details, email verification status and password.",
};

export default function AccountProfilePage() {
  return (
    <>
      <section className="border-b border-line bg-white/50">
        <div className="shell py-14 sm:py-20">
          <div className="grid gap-8 lg:grid-cols-[1fr_0.8fr] lg:items-end">
            <div>
              <p className="eyebrow text-terracotta">Your account</p>
              <h1 className="mt-3 font-display text-5xl leading-[0.98] tracking-tight text-charcoal sm:text-6xl">
                Profile
              </h1>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-muted">
                Keep your contact details current so we can reach you about a journey, and manage
                the password for your account.
              </p>
            </div>
            <p className="max-w-sm text-sm leading-6 text-muted lg:justify-self-end">
              Your email address is also where verification and booking messages are sent.
            </p>
          </div>
        </div>
      </section>

      <section className="shell py-10 sm:py-14">
        <Suspense fallback={<ProfileSkeleton />}>
          <AccountProfile />
        </Suspense>
      </section>
    </>
  );
}
