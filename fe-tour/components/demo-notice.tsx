import { IconSparkles } from "./icons";

export function DemoNotice() {
  return (
    <div className="mb-8 flex items-start gap-3 border-l-2 border-terracotta bg-white/70 px-4 py-4 text-sm leading-6 text-charcoal">
      <IconSparkles className="mt-0.5 h-4 w-4 shrink-0 text-terracotta" />
      <p>
        <span className="font-semibold">Showing sample journeys.</span> Live availability is
        unavailable right now, so these dates and prices are illustrative only.
      </p>
    </div>
  );
}
