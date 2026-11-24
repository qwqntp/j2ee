export function lastAvailablePage(page: number, totalPages: number): number {
  return Math.max(0, Math.min(page, totalPages - 1));
}
