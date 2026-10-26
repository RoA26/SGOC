/** Iconos de trazo propios (24×24). Se dibujan con currentColor para heredar el color del texto. */
const PATHS = {
  shield: "M12 3 4.5 6v5.5c0 4.6 3.1 8.3 7.5 9.5 4.4-1.2 7.5-4.9 7.5-9.5V6L12 3Z M9 12l2 2 4-4",
  boxes: "M3.5 8.5 12 4l8.5 4.5v7L12 20l-8.5-4.5v-7Z M3.5 8.5 12 13l8.5-4.5 M12 13v7",
  warehouse: "M3 20V9l9-5 9 5v11 M7 20v-7h10v7 M7 16.5h10",
  clipboard: "M9 4h6v3H9z M7 5.5H5.5V21h13V5.5H17 M9 13l2 2 4-4",
  chart: "M4 20h16 M7 16v-5 M12 16V7 M17 16v-8",
  entrada: "M12 4v11 M7.5 10.5 12 15l4.5-4.5 M4 19.5h16",
  salida: "M12 15V4 M7.5 8.5 12 4l4.5 4.5 M4 19.5h16",
  ajuste: "M5 7h9 M18 7h1 M5 17h3 M12 17h7 M16 5v4 M10 15v4",
  history: "M4 12a8 8 0 1 0 2.4-5.7 M4 4.5v4h4 M12 8v4.5l3 2",
  users: "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z M2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5 M16 4.3a3.5 3.5 0 0 1 0 6.4 M18.5 14.8c1.6.8 2.7 2.5 3 5.2",
  building: "M5 21V4h10v17 M15 9h4v12 M3 21h18 M8.5 8h3 M8.5 12h3 M8.5 16h3",
  logout: "M14 4h4.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H14 M10 16.5 5.5 12 10 7.5 M5.5 12H15",
  search: "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14Z M20 20l-4-4",
  check: "M5 12.5 10 17.5 19 7",
  alert: "M12 4 2.8 19.5h18.4L12 4Z M12 10v4.5 M12 17.2v.3",
  info: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z M12 11v5.5 M12 7.7v.3",
  lock: "M6 11h12v9.5H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3",
  menu: "M4 7h16 M4 12h16 M4 17h16",
  close: "M6 6l12 12 M18 6 6 18",
  arrowRight: "M5 12h14 M13 6l6 6-6 6",
  undo: "M9 14 4 9l5-5 M4 9h10.5a5.5 5.5 0 0 1 0 11H11",
  package: "M4 7.5 12 3.5l8 4v9l-8 4-8-4v-9Z M4 7.5l8 4 8-4 M12 11.5v9 M8 5.5l8 4",
  home: "M4 11 12 4l8 7 M6 9.5V20h12V9.5",
  plus: "M12 5v14 M5 12h14",
  trace: "M5 5h4v4H5z M15 15h4v4h-4z M9 7h4a2 2 0 0 1 2 2v6",
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, className = "h-5 w-5", label }: { name: IconName; className?: string; label?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
