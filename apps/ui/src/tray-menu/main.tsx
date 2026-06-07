import React from "react";
import ReactDOM from "react-dom/client";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { TrayMenu } from "./TrayMenu";
import "../styles.css";
import "flag-icons/css/flag-icons.min.css";
import "./tray-menu.css";

// The flyout is a menu, not a page: suppress its own context menu and any
// text selection / drag affordances.
document.addEventListener("contextmenu", (event) => event.preventDefault());
document.addEventListener("dragstart", (event) => event.preventDefault());

try {
  void getCurrentWebview().setBackgroundColor([32, 34, 49, 255]).catch(() => undefined);
} catch {
  // Browser preview does not have a Tauri webview.
}

ReactDOM.createRoot(document.getElementById("tray-root") as HTMLElement).render(
  <React.StrictMode>
    <TrayMenu />
  </React.StrictMode>,
);
