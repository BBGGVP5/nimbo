import { useNavigate } from "react-router-dom";
import { useMessages } from "../lib/i18n";

export function BackButton() {
  const navigate = useNavigate();
  const m = useMessages();
  return (
    <button
      type="button"
      className="page-back-button"
      onClick={() => navigate(-1)}
      aria-label={m.common.back}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M15 18l-6-6 6-6" />
      </svg>
      <span>{m.common.back}</span>
    </button>
  );
}
