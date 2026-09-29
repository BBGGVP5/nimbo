/** The approved cloud is a connection state, never an optimistic loading state. */
export function ConnectionStateIcon({ connected, busy = false }: { connected: boolean; busy?: boolean }) {
  if (busy) return <span className="connection-state-loader" data-connection-icon="loading" aria-hidden="true" />;
  if (connected) return <svg className="connection-state-icon" data-connection-icon="cloud" viewBox="160 160 704 704" fill="currentColor" aria-hidden="true">
    <path d="M329 728C244 728 184 669 184 587C184 507 254 429 344 428C370 350 440 296 526 296C620 296 682 357 694 454C777 452 840 512 840 591C840 624 830 650 809 657C769 672 673 639 630 601C603 577 590 548 593 512C569 531 570 569 588 600C624 663 687 704 764 706C740 722 710 728 681 728Z" />
  </svg>;
  return <svg className="connection-state-icon" data-connection-icon="power" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true"><path d="M12 3v9M6.4 5.9a8 8 0 1 0 11.2 0" /></svg>;
}
