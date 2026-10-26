type Props = { className?: string; withWordmark?: boolean; tone?: "light" | "dark" };

/** Isotipo de Bastión: un baluarte visto en planta que resguarda existencias apiladas. */
export function Isotipo({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" fill="none" className={className} aria-hidden="true" focusable="false">
      <path d="M16 2.5 29 11.2 25 29.5H7L3 11.2 16 2.5Z" fill="var(--b-ink)" />
      <path
        d="M16 2.5 29 11.2 25 29.5H7L3 11.2 16 2.5Z"
        stroke="var(--b-accent)"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <rect x="10" y="13" width="12" height="3" rx="0.6" fill="var(--b-accent)" />
      <rect x="10" y="18" width="12" height="3" rx="0.6" fill="#e8eef4" />
      <rect x="10" y="23" width="12" height="3" rx="0.6" fill="#e8eef4" opacity="0.55" />
    </svg>
  );
}

export function Logo({ className = "", withWordmark = true, tone = "dark" }: Props) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <Isotipo />
      {withWordmark && (
        <span
          className={`text-[1.15rem] font-extrabold tracking-[-0.02em] ${tone === "light" ? "text-white" : "text-ink"}`}
        >
          Bastión
        </span>
      )}
    </span>
  );
}
