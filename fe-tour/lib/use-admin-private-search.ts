"use client";

import { useCallback, useState, useSyncExternalStore } from "react";
import { privateSearchStore } from "./admin-private-search";

const subscribe = () => () => {};
const serverSnapshot = () => null;

export function useAdminPrivateSearch(accountId: number, page: string) {
  const read = useCallback(() => privateSearchStore().read(accountId, page), [accountId, page]);
  const stored = useSyncExternalStore(subscribe, read, serverSnapshot);
  const [override, setOverride] = useState<{ accountId: number; page: string; value: string } | null>(null);
  const value = override?.accountId === accountId && override.page === page ? override.value : stored;
  const setValue = useCallback((next: string) => {
    privateSearchStore().write(accountId, page, next);
    setOverride({ accountId, page, value: next });
  }, [accountId, page]);
  return [value, setValue] as const;
}
