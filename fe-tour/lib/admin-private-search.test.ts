import { afterEach, describe, expect, it, vi } from "vitest";
import { createPrivateSearchStore } from "./admin-private-search";
import { clearPerUserClientState } from "./user-state";

function memoryStorage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() { return values.size; },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => { values.delete(key); },
    setItem: (key, value) => { values.set(key, value); },
  };
}

describe("private admin searches", () => {
  afterEach(() => vi.unstubAllGlobals());
  it("isolates searches by account and page and supports resetting one page", () => {
    const store = createPrivateSearchStore(memoryStorage());
    store.write(1, "customers", "private");
    store.write(1, "payments", "other");
    expect(store.read(2, "customers")).toBe("");
    expect(store.read(1, "customers")).toBe("private");
    store.write(1, "customers", "");
    expect(store.read(1, "customers")).toBe("");
    expect(store.read(1, "payments")).toBe("other");
  });

  it("removes all private searches while leaving unrelated data intact", () => {
    const storage = memoryStorage();
    const store = createPrivateSearchStore(storage);
    storage.setItem("unrelated", "value");
    store.write(1, "customers", "private");
    store.clear();
    expect(store.read(1, "customers")).toBe("");
    expect(storage.getItem("unrelated")).toBe("value");
  });

  it("gracefully handles unavailable storage", () => {
    const store = createPrivateSearchStore(null);
    expect(() => store.write(1, "customers", "private")).not.toThrow();
    expect(store.read(1, "customers")).toBe("");
    expect(() => store.clear()).not.toThrow();
  });

  it("handles storage methods throwing without exposing an error", () => {
    const storage = {
      get length(): number { throw new Error("blocked"); },
      getItem: () => { throw new Error("blocked"); },
      setItem: () => { throw new Error("blocked"); },
      removeItem: () => { throw new Error("blocked"); },
      key: () => { throw new Error("blocked"); },
      clear: () => { throw new Error("blocked"); },
    } as Storage;
    const store = createPrivateSearchStore(storage);
    expect(store.read(1, "customers")).toBe("");
    expect(() => store.write(1, "customers", "private")).not.toThrow();
    expect(() => store.clear()).not.toThrow();
  });

  it("is removed when the current account is cleared or switched", () => {
    const storage = memoryStorage();
    vi.stubGlobal("window", { sessionStorage: storage });
    const store = createPrivateSearchStore(storage);
    store.write(1, "customers", "private");
    clearPerUserClientState();
    expect(store.read(1, "customers")).toBe("");
  });
});
