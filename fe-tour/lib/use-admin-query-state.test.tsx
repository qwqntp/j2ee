import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdminQueryState } from "./use-admin-query-state";

const navigation = vi.hoisted(() => ({
  search: "",
  push: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/test",
  useRouter: () => ({ push: navigation.push, replace: navigation.replace }),
  useSearchParams: () => new URLSearchParams(navigation.search),
}));

const schema = {
  page: { kind: "page" },
  q: { kind: "enum", allowed: [] },
} as const;

function ListState() {
  const { params } = useAdminQueryState(schema);
  return <output>{params.toString()}</output>;
}

describe("useAdminQueryState", () => {
  beforeEach(() => {
    navigation.search = "";
    navigation.push.mockClear();
    navigation.replace.mockClear();
  });

  it("renders committed URL state without exposing a sensitive search", () => {
    navigation.search = "page=3&q=private&other=kept";
    expect(renderToStaticMarkup(<ListState />)).toBe("<output>page=3&amp;other=kept</output>");
  });

  it("renders a safe first page for malformed input", () => {
    navigation.search = "page=-2";
    expect(renderToStaticMarkup(<ListState />)).toBe("<output></output>");
  });
});
