import type { ReactNode } from "react";
import { serverCountryCode } from "../lib/api";

type CountryFlagProps = {
  serverName: string;
  fallback: ReactNode;
  className?: string;
};

export function CountryFlag({ serverName, fallback, className = "" }: CountryFlagProps) {
  const countryCode = serverCountryCode(serverName);
  if (!countryCode) return <>{fallback}</>;

  return (
    <span
      className={["fi", `fi-${countryCode.toLowerCase()}`, "country-flag", className].filter(Boolean).join(" ")}
      aria-label={countryCode}
      title={countryCode}
    />
  );
}
