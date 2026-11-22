export function getDrawerFocusIndex(
  currentIndex: number,
  length: number,
  backwards: boolean,
): number | null {
  if (length === 0) return null;
  if (currentIndex < 0) return backwards ? length - 1 : 0;
  return (currentIndex + (backwards ? -1 : 1) + length) % length;
}
