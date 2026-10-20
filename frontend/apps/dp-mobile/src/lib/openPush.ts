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

import { router } from "expo-router";
import { cnsReqIdOf } from "./push";

/**
 * What tapping a push does, whether a real notification (PushTaps) or the dev push relay's banner: fetch the list
 * again, since the push carries no details (ADR-032 §4), then open the request it names, or the list.
 */
export function openPush(data: Record<string, unknown> | null | undefined, reload: () => Promise<void>): void {
  reload().catch(() => undefined);
  const id = cnsReqIdOf(data);
  if (id) router.navigate({ pathname: "/consent/[id]", params: { id } });
  else router.navigate("/");
}
