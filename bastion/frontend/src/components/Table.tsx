import type { ReactNode } from "react";

export function Table({ head, children, caption }: { head: ReactNode; children: ReactNode; caption: string }) {
  return (
    <div className="relative overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="border-b border-line bg-surface-2/60 text-left text-xs font-semibold uppercase tracking-wide text-text-2">
          {head}
        </thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  );
}

export const th = "px-4 py-3 font-semibold";
export const td = "px-4 py-3 align-middle";
