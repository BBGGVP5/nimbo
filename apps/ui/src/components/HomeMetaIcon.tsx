/** Quiet metadata glyphs from the approved workspace composition. */
export function HomeMetaIcon({ kind }: { kind: "clock" | "ping" | "route" | "traffic" | "calendar" | "theme" }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === "clock" ? <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>
      : kind === "ping" ? <path d="M2 12h4l3-8 5 16 3-8h5"/>
      : kind === "traffic" ? <path d="M5 20v-5m5 5V9m5 11V5m5 15V2"/>
      : kind === "calendar" ? <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 11h18m-13 4h.01M12 15h.01M16 15h.01"/></>
      : kind === "route" ? <><circle cx="5" cy="6" r="3"/><circle cx="19" cy="18" r="3"/><path d="M8 6h9a4 4 0 0 1 0 8H7a4 4 0 0 0 0 8h6"/></>
      : <><path d="M20 12a8 8 0 1 1-8-8 6 6 0 0 0 8 8Z"/><path d="M17 2v4m-2-2h4"/></>}
  </svg>;
}
