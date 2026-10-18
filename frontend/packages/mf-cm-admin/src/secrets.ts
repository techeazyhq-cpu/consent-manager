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

/**
 * What onboarding hands to the tenant's page it opens next, in memory only and read once:
 * <ul>
 *   <li>the API client secret, shown once straight after activation and never fetched again;</li>
 *   <li>a step that failed after the tenant was registered (its logo, or activation), to finish there.</li>
 * </ul>
 */
const secrets = new Map<string, string>();
const notices = new Map<string, string>();

export function rememberSecret(dfId: string, secret: string) {
  secrets.set(dfId, secret);
}

export function takeSecret(dfId: string): string | undefined {
  const secret = secrets.get(dfId);
  secrets.delete(dfId);
  return secret;
}

export function rememberNotice(dfId: string, notice: string) {
  notices.set(dfId, notice);
}

export function takeNotice(dfId: string): string | undefined {
  const notice = notices.get(dfId);
  notices.delete(dfId);
  return notice;
}
