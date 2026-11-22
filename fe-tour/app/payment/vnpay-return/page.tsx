import type { Metadata } from "next";
import { Suspense } from "react";
import { VnpayReturn, VnpayReturnLoading } from "@/components/vnpay-return";

export const metadata: Metadata = {
  title: "VNPay payment status",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function VnpayReturnPage() {
  return (
    <section className="shell py-12 sm:py-16" aria-label="VNPay payment status">
      <Suspense fallback={<VnpayReturnLoading />}>
        <VnpayReturn />
      </Suspense>
    </section>
  );
}
