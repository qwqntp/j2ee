export function TourGridSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">Loading journeys…</span>
      <div className="grid gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
        {Array.from({ length: count }).map((_, index) => (
          <div key={index}>
            <div className="aspect-[4/3] animate-pulse rounded-[3px] bg-sage/25" />
            <div className="space-y-3 pt-5">
              <div className="h-3 w-1/3 animate-pulse rounded-[2px] bg-sage/25" />
              <div className="h-6 w-2/3 animate-pulse rounded-[2px] bg-sage/25" />
              <div className="h-4 w-full animate-pulse rounded-[2px] bg-sage/25" />
              <div className="h-4 w-4/5 animate-pulse rounded-[2px] bg-sage/25" />
              <div className="flex justify-between gap-4 border-t border-line pt-4">
                <div className="h-8 w-28 animate-pulse rounded-[2px] bg-sage/25" />
                <div className="h-8 w-24 animate-pulse rounded-[2px] bg-sage/25" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function TourDetailSkeleton() {
  return (
    <div role="status" aria-live="polite" className="shell py-12 sm:py-16">
      <span className="sr-only">Loading journey details…</span>
      <div aria-hidden="true" className="space-y-6">
        <div className="h-3 w-40 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="h-12 w-3/4 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="h-5 w-2/3 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="aspect-[16/7] animate-pulse rounded-[3px] bg-sage/25" />
        <div className="grid gap-8 lg:grid-cols-[1.6fr_1fr]">
          <div className="space-y-4">
            <div className="h-6 w-1/2 animate-pulse rounded-[2px] bg-sage/25" />
            <div className="h-4 w-full animate-pulse rounded-[2px] bg-sage/25" />
            <div className="h-4 w-11/12 animate-pulse rounded-[2px] bg-sage/25" />
            <div className="h-4 w-4/5 animate-pulse rounded-[2px] bg-sage/25" />
          </div>
          <div className="h-64 animate-pulse rounded-[3px] bg-sage/25" />
        </div>
      </div>
    </div>
  );
}

export function CheckoutSkeleton() {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">Loading checkout…</span>
      <div aria-hidden="true" className="grid gap-10 lg:grid-cols-[1.35fr_0.65fr]">
        <div className="space-y-6">
          <div className="h-10 w-1/2 animate-pulse rounded-[2px] bg-sage/25" />
          <div className="h-4 w-2/3 animate-pulse rounded-[2px] bg-sage/25" />
          <div className="h-56 animate-pulse rounded-[3px] bg-sage/25" />
          <div className="h-56 animate-pulse rounded-[3px] bg-sage/25" />
        </div>
        <div className="h-96 animate-pulse rounded-[3px] bg-sage/25" />
      </div>
    </div>
  );
}

export function MyTripsSkeleton() {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">Loading your trips…</span>
      <div aria-hidden="true" className="space-y-4">
        <div className="h-9 w-48 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="h-11 w-full animate-pulse rounded-[2px] bg-sage/25" />
        {Array.from({ length: 3 }).map((_, index) => (
          <div
            key={index}
            className="grid gap-4 rounded-[4px] border border-line bg-white p-4 sm:grid-cols-[15rem_1fr]"
          >
            <div className="aspect-[4/3] animate-pulse rounded-[3px] bg-sage/25 sm:aspect-auto" />
            <div className="space-y-3 py-2">
              <div className="h-3 w-28 animate-pulse rounded-[2px] bg-sage/25" />
              <div className="h-7 w-2/3 animate-pulse rounded-[2px] bg-sage/25" />
              <div className="h-4 w-1/2 animate-pulse rounded-[2px] bg-sage/25" />
              <div className="h-4 w-3/4 animate-pulse rounded-[2px] bg-sage/25" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function TripDetailSkeleton() {
  return (
    <div role="status" aria-live="polite" className="shell py-10 sm:py-12">
      <span className="sr-only">Loading trip details…</span>
      <div aria-hidden="true" className="space-y-6">
        <div className="h-3 w-40 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="h-12 w-3/4 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="h-5 w-1/2 animate-pulse rounded-[2px] bg-sage/25" />
        <div className="grid gap-8 lg:grid-cols-[1.5fr_1fr]">
          <div className="space-y-4">
            <div className="h-40 animate-pulse rounded-[3px] bg-sage/25" />
            <div className="h-40 animate-pulse rounded-[3px] bg-sage/25" />
          </div>
          <div className="h-80 animate-pulse rounded-[3px] bg-sage/25" />
        </div>
      </div>
    </div>
  );
}

export function ProfileSkeleton() {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">Loading your profile…</span>
      <div aria-hidden="true" className="grid gap-8 lg:grid-cols-[1.15fr_0.85fr]">
        <div className="h-[34rem] animate-pulse rounded-[4px] bg-sage/20" />
        <div className="h-80 animate-pulse rounded-[4px] bg-sage/20" />
      </div>
    </div>
  );
}
