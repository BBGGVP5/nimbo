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
      <aside className="w-60 shrink-0 flex flex-col p-3">
        <div className="glass rounded-2xl flex-1 flex flex-col">
          <div className="px-5 pt-5 pb-4">
            <div className="text-lg font-semibold tracking-tight bg-gradient-to-r from-[var(--color-accent-bright)] to-[var(--color-status-connected)] bg-clip-text text-transparent">
              Nimbo
            </div>
            <div className="text-[11px] text-[var(--color-text-faint)] font-mono mt-0.5">
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
                    "block px-3 py-2 my-0.5 rounded-xl text-sm transition-all",
                    isActive
                      ? "bg-[var(--color-glass-bg-strong)] text-[var(--color-text)] shadow-[0_0_20px_var(--color-glow-accent)]"
                      : "text-[var(--color-text-dim)] hover:text-[var(--color-text)] hover:bg-[var(--color-glass-bg)]",
                  ].join(" ")
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="px-5 py-4 text-[10px] text-[var(--color-text-faint)] font-mono uppercase tracking-wider">
            private build
          </div>
        </div>
      </aside>

      <main className="flex-1 overflow-auto p-3 pl-0">
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
