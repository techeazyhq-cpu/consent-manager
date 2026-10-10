/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
