import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST || "127.0.0.1";

export default defineConfig(async () => ({
  plugins: [react(), tailwindcss()],
  clearScreen: false,
  // Two HTML entry points: the main app window and the tray popup flyout.
  build: {
    // Флаги стран приходят из flag-icons отдельными SVG. По умолчанию мелкие
    // файлы встраиваются прямо в CSS, и около четырёхсот килобайт таблицы
    // стилей — это две с половиной сотни флагов, которые разбираются до
    // первого кадра, хотя на экране видно от силы десяток. Пусть остаются
    // файлами: браузер возьмёт с диска только те, что понадобились.
    assetsInlineLimit: (filePath: string) =>
      filePath.includes("flag-icons") ? false : undefined,
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL("./index.html", import.meta.url)),
        "tray-menu": fileURLToPath(new URL("./tray-menu.html", import.meta.url)),
      },
    },
  },
  server: {
    port: 1420,
    strictPort: true,
    host,
    hmr: host
      ? {
          protocol: "ws",
          host,
          port: 1421,
        }
      : undefined,
    watch: {
      ignored: ["**/src-tauri/**"],
    },
  },
}));
