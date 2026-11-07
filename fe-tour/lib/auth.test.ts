import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Account, AuthSession } from "./types";

const api = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  refresh: vi.fn(),
  getMe: vi.fn(),
}));

vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api")>();
  return {
    ...actual,
    login: api.login,
    register: api.register,
    logout: api.logout,
    refresh: api.refresh,
    getMe: api.getMe,
  };
});

const account: Account = {
  id: 1,
  username: "lan",
  email: "lan@example.com",
  emailVerified: 0,
  status: 1,
  roleId: 2,
  roleName: "CUSTOMER",
  createdAt: null,
  updatedAt: null,
};

const session: AuthSession = { accessToken: "access-token", account };

function installBrowser() {
  const data = new Map<string, string>();
  vi.stubGlobal("window", {
    localStorage: {
      getItem: (key: string) => data.get(key) ?? null,
      setItem: (key: string, value: string) => {
        data.set(key, value);
      },
      removeItem: (key: string) => {
        data.delete(key);
      },
    },
    addEventListener: vi.fn(),
  });
  return data;
}

function seedSession(data: Map<string, string>, value: AuthSession): void {
  data.set("tour_access_token", value.accessToken);
  data.set("tour_account", JSON.stringify(value.account));
}

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("auth session", () => {
  it("stores the session returned by signIn", async () => {
    installBrowser();
    api.login.mockResolvedValue(session);
    const auth = await import("./auth");

    await auth.signIn({ username: "lan", password: "secret" });

    expect(auth.getStoredSession()).toEqual(session);
  });

  it("clears the local session even when logout fails", async () => {
    const data = installBrowser();
    seedSession(data, session);
    api.logout.mockRejectedValue(new Error("offline"));
    const auth = await import("./auth");

    await expect(auth.signOut()).rejects.toThrow("offline");
    expect(auth.getStoredSession()).toBeNull();
  });

  it("refreshes the stored account when the access token is valid", async () => {
    const data = installBrowser();
    seedSession(data, session);
    api.getMe.mockResolvedValue({ ...account, username: "lan-renamed" });
    const auth = await import("./auth");

    const current = await auth.ensureSession();

    expect(current?.account.username).toBe("lan-renamed");
    expect(auth.getStoredSession()?.account.username).toBe("lan-renamed");
  });

  it("falls back to the refresh cookie when the access token expired", async () => {
    const data = installBrowser();
    seedSession(data, { accessToken: "expired", account });
    const { ApiError } = await import("./api");
    api.getMe.mockRejectedValue(new ApiError(401, "expired"));
    api.refresh.mockResolvedValue({ accessToken: "fresh", account });
    const auth = await import("./auth");

    const current = await auth.ensureSession();

    expect(current?.accessToken).toBe("fresh");
    expect(auth.getStoredSession()?.accessToken).toBe("fresh");
  });

  it("keeps the cached session when the auth service is unreachable", async () => {
    const data = installBrowser();
    seedSession(data, session);
    api.getMe.mockRejectedValue(new TypeError("fetch failed"));
    const auth = await import("./auth");

    const current = await auth.ensureSession();

    expect(current).toEqual(session);
    expect(auth.getStoredSession()).toEqual(session);
    expect(api.refresh).not.toHaveBeenCalled();
  });

  it("clears the session when both tokens are rejected", async () => {
    const data = installBrowser();
    seedSession(data, session);
    const { ApiError } = await import("./api");
    api.getMe.mockRejectedValue(new ApiError(401, "expired"));
    api.refresh.mockRejectedValue(new ApiError(401, "invalid refresh"));
    const auth = await import("./auth");

    const current = await auth.ensureSession();

    expect(current).toBeNull();
    expect(auth.getStoredSession()).toBeNull();
  });
});
