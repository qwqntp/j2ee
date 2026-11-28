export function isDialogBackdropClick(
  rect: Pick<DOMRect, "left" | "right" | "top" | "bottom">,
  x: number,
  y: number,
): boolean {
  return x < rect.left || x > rect.right || y < rect.top || y > rect.bottom;
}
