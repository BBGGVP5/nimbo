/** Canonical round-trip glyph: use the same paths for footer, home and proxy cards. */
export function PingIcon({className='',pending=false}:{className?:string;pending?:boolean}) {
  return <svg className={`${className}${pending?' is-pending':''}`.trim()||undefined} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M7 20V7m0 0L3.5 10.5M7 7l3.5 3.5"/>
    <path d="M17 4v13m0 0 3.5-3.5M17 17l-3.5-3.5"/>
  </svg>;
}
