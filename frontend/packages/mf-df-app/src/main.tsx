import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "ui-kit/src/styles.css";
import App from "./App";

const GW_BASE = import.meta.env.VITE_GW_BASE_URL ?? "http://localhost:9000";
const KC_BASE = import.meta.env.VITE_KC_BASE_URL ?? "http://localhost:8080";
const KC_REALM = import.meta.env.VITE_KC_REALM ?? "cm-realm";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App gwBase={GW_BASE} kcBase={KC_BASE} realm={KC_REALM} />
  </StrictMode>,
);
