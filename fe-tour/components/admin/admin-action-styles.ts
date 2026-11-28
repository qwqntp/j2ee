const base = "inline-flex size-11 cursor-pointer items-center justify-center rounded-lg border border-current bg-white transition-colors hover:border-transparent hover:text-white disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-white";

export const adminActionStyles = {
  view: `${base} text-[#0084ff] hover:bg-[#0084ff]`,
  edit: `${base} text-charcoal hover:bg-charcoal`,
  delete: `${base} text-red-800 hover:bg-red-800 disabled:hover:text-red-800`,
};
