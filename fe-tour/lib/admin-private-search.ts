const PREFIX = "admin-private-search:";

export function createPrivateSearchStore(storage: Storage | null) {
  function key(accountId: number, page: string) {
    return `${PREFIX}${accountId}:${page}`;
  }

  return {
    read(accountId: number, page: string): string {
      try { return storage?.getItem(key(accountId, page))?.slice(0, 100) ?? ""; }
      catch { return ""; }
    },
    write(accountId: number, page: string, value: string): void {
      try {
        if (value) storage?.setItem(key(accountId, page), value.slice(0, 100));
        else storage?.removeItem(key(accountId, page));
      } catch { /* Storage may be disabled or full. */ }
    },
    clear(): void {
      try {
        if (!storage) return;
        for (let i = storage.length - 1; i >= 0; i--) {
          const entry = storage.key(i);
          if (entry?.startsWith(PREFIX)) storage.removeItem(entry);
        }
      } catch { /* Storage may be disabled. */ }
    },
  };
}

export function privateSearchStore() {
  try {
    return createPrivateSearchStore(typeof window === "undefined" ? null : window.sessionStorage);
  } catch {
    return createPrivateSearchStore(null);
  }
}
