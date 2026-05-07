import { NavLink, Route, Routes, Navigate } from "react-router-dom";
import { Home } from "./pages/Home";
import { Subscriptions } from "./pages/Subscriptions";
import { Settings } from "./pages/Settings";

const navItems = [
  { to: "/", label: "Главная", end: true },
  { to: "/subscriptions", label: "Подписки", end: false },
  { to: "/settings", label: "Настройки", end: false },
];

export default function App() {
  return (
    <div className="flex h-full">
      <aside className="w-56 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface)] flex flex-col">
        <div className="px-5 py-5">
          <div className="text-lg font-semibold tracking-tight">Nimbo</div>
          <div className="text-xs text-[var(--color-text-dim)] font-mono mt-0.5">
            v0.1.0 · disconnected
          </div>
        </div>
        <nav className="flex-1 px-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                [
                  "block px-3 py-2 rounded-md text-sm transition-colors",
                  isActive
                    ? "bg-[var(--color-surface-2)] text-[var(--color-text)]"
                    : "text-[var(--color-text-dim)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface-2)]/60",
                ].join(" ")
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="px-5 py-4 text-[11px] text-[var(--color-text-dim)] font-mono border-t border-[var(--color-border)]">
          private build
        </div>
      </aside>

      <main className="flex-1 overflow-auto">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/subscriptions" element={<Subscriptions />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
