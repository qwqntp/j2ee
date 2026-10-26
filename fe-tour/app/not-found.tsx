import { EmptyState } from "@/components/empty-state";

export default function NotFound() {
  return (
    <section className="shell py-20 sm:py-28">
      <div className="mx-auto max-w-2xl">
        <p className="eyebrow text-center text-terracotta">404</p>
        <div className="mt-6">
          <EmptyState
            title="This path leads nowhere."
            description="The page may have moved, or the journey is no longer published. The rest of the collection is still here."
            action={{ href: "/tours", label: "Browse journeys" }}
          />
        </div>
      </div>
    </section>
  );
}
