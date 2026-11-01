"use client";

import {
  getMe,
  login as apiLogin,
  logout as apiLogout,
  refresh as apiRefresh,
  register as apiRegister,
} from "./api";
import type { Account, AuthSession } from "./types";

const TOKEN_KEY = "tour_access_token";
const ACCOUNT_KEY = "tour_account";

export function getStoredSession(): AuthSession | null {
  if (typeof window === "undefined") return null;
  const accessToken = window.localStorage.getItem(TOKEN_KEY);
  const rawAccount = window.localStorage.getItem(ACCOUNT_KEY);
  if (!accessToken || !rawAccount) return null;
  try {
    return { accessToken, account: JSON.parse(rawAccount) as Account };
  } catch {
    return null;
  }
}

function storeSession(session: AuthSession): void {
  window.localStorage.setItem(TOKEN_KEY, session.accessToken);
  window.localStorage.setItem(ACCOUNT_KEY, JSON.stringify(session.account));
}

export function clearSession(): void {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(ACCOUNT_KEY);
}

export async function signIn(input: {
  username: string;
  password: string;
}): Promise<AuthSession> {
  const session = await apiLogin(input);
  storeSession(session);
  return session;
}

export async function signUp(input: {
  username: string;
  password: string;
  email: string;
}): Promise<AuthSession> {
  const session = await apiRegister(input);
  storeSession(session);
  return session;
}

export async function signOut(): Promise<void> {
  const session = getStoredSession();
  try {
    await apiLogout(session?.accessToken);
  } finally {
    clearSession();
  }
}

export async function ensureSession(): Promise<AuthSession | null> {
  const stored = getStoredSession();
  if (stored) {
    try {
      await getMe(stored.accessToken);
      return stored;
    } catch {
      // Fall through to the refresh cookie.
    }
  }

  try {
    const session = await apiRefresh();
    storeSession(session);
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export async function currentAccount(): Promise<Account | null> {
  const session = await ensureSession();
  if (!session) return null;
  try {
    return await getMe(session.accessToken);
  } catch {
    clearSession();
    return null;
  }
}
