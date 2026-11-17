import type { Promotion } from "./types";

export type PromotionLifecycle = "Inactive" | "Scheduled" | "Expired" | "Active";

function dateOnly(value: string | Date): Date {
  const yyyyMmDd =
    typeof value === "string"
      ? value
      : `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
  return new Date(`${yyyyMmDd}T00:00:00`);
}

export function promotionLifecycle(
  promotion: Pick<Promotion, "active" | "startDate" | "endDate" | "code">,
  now: Date = new Date(),
): PromotionLifecycle {
  if (promotion.active === false) return "Inactive";

  const today = dateOnly(now);
  if (dateOnly(promotion.startDate) > today) return "Scheduled";
  if (dateOnly(promotion.endDate) < today) return "Expired";
  return "Active";
}
