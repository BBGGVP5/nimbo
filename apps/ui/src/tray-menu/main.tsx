import React from "react";
import ReactDOM from "react-dom/client";
import { TrayMenu } from "./TrayMenu";
import "../styles.css";
import "./tray-menu.css";

// The flyout is a menu, not a page: suppress its own context menu and any
// text selection / drag affordances.
document.addEventListener("contextmenu", (event) => event.preventDefault());
document.addEventListener("dragstart", (event) => event.preventDefault());

ReactDOM.createRoot(document.getElementById("tray-root") as HTMLElement).render(
  <React.StrictMode>
    <TrayMenu />
  </React.StrictMode>,
);
