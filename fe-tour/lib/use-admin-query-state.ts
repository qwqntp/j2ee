"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo } from "react";
import { normalizeAdminQuery, updateAdminQuery, type AdminQuerySchema } from "./admin-query-state";

export function useAdminQueryState(schema: AdminQuerySchema) {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const raw = searchParams.toString();
  const normalized = useMemo(
    () => normalizeAdminQuery(new URLSearchParams(raw), schema).toString(),
    [raw, schema],
  );

  useEffect(() => {
    if (raw === normalized) return;
    router.replace(normalized ? `${pathname}?${normalized}` : pathname, { scroll: false });
  }, [raw, normalized, pathname, router]);

  const update = useCallback((patch: Record<string, string | null>, mode: "push" | "replace" = "push") => {
    const next = updateAdminQuery(new URLSearchParams(raw), schema, patch).toString();
    if (next === raw) return;
    const url = next ? `${pathname}?${next}` : pathname;
    if (mode === "replace") router.replace(url, { scroll: false });
    else router.push(url, { scroll: false });
  }, [raw, schema, pathname, router]);

  return { params: new URLSearchParams(normalized), update };
}
