"use client";

import {
  ApiError,
  getMe,
  login as apiLogin,
  logout as apiLogout,
  refresh as apiRefresh,
  register as apiRegister,
} from "./api";
import { clearSession, getStoredSession, saveSession, useSession } from "./session";
import type { Account, AuthSession } from "./types";

export { clearSession, getStoredSession, useSession };

export async function signIn(input: {
  username: string;
  password: string;
}): Promise<AuthSession> {
  const session = await apiLogin(input);
  saveSession(session);
  return session;
}

export async function signUp(input: {
  username: string;
  password: string;
  email: string;
}): Promise<AuthSession> {
  const session = await apiRegister(input);
  saveSession(session);
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
      const account = await getMe(stored.accessToken);
      const session = { accessToken: stored.accessToken, account };
      saveSession(session);
      return session;
    } catch (error) {
      if (!isUnauthorized(error)) {
        // A timeout or outage should not sign the visitor out.
        return stored;
      }
    }
  }

  try {
    const session = await apiRefresh();
    saveSession(session);
    return session;
  } catch (error) {
    if (isUnauthorized(error)) {
      clearSession();
      return null;
    }
    return stored;
  }
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
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
