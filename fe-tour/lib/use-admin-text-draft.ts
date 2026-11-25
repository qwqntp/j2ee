"use client";

import { useEffect, useRef, useState } from "react";

export function useAdminTextDraft(committed: string, commit: (value: string) => void, navigationKey = committed) {
  const [draft, setDraft] = useState<{ base: string; navigationKey: string; value: string } | null>(null);
  const [previousNavigation, setPreviousNavigation] = useState(navigationKey);
  if (previousNavigation !== navigationKey) {
    setPreviousNavigation(navigationKey);
    setDraft(null);
  }
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latestCommit = useRef(commit);
  useEffect(() => { latestCommit.current = commit; }, [commit]);
  useEffect(() => () => {
    if (timer.current !== null) clearTimeout(timer.current);
  }, [committed, navigationKey]);
  function change(value: string) {
    setDraft({ base: committed, navigationKey, value });
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = setTimeout(() => { timer.current = null; latestCommit.current(value); }, 400);
  }
  return [draft?.base === committed && draft.navigationKey === navigationKey ? draft.value : committed, change] as const;
}
