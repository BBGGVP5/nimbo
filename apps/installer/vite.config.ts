import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const host = process.env.TAURI_DEV_HOST || "127.0.0.1";

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: {
    port: 1430,
    strictPort: true,
    host,
    hmr: host
      ? {
          protocol: "ws",
          host,
          port: 1431,
        }
      : undefined,
    watch: {
      ignored: ["**/src-tauri/**"],
    },
  },
});
