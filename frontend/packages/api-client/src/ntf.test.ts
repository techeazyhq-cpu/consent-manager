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

import { afterEach, describe, expect, it, vi } from "vitest";
import { ntf, type PushPltf } from "./index";

describe("push devices", () => {
  afterEach(() => vi.unstubAllGlobals());

  it.each<PushPltf>(["WEB", "ANDROID", "IOS"])("registers a %s device's token with its platform", async (platform) => {
    const fetch = vi.fn(async () =>
      new Response(JSON.stringify({ id: "d1", platform, registeredAt: "t", lastSeenAt: "t" }), { status: 201 }),
    );
    vi.stubGlobal("fetch", fetch);

    const dvc = await ntf.registerPushDvc("http://gw", "jwt", { token: "fcm-token", platform });

    expect(dvc.platform).toBe(platform);
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://gw/api/ntf/push-dvcs");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ token: "fcm-token", platform });
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer jwt");
  });

  it("removes a device by its id", async () => {
    const fetch = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);

    await ntf.removePushDvc("http://gw", "jwt", "d/1");

    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://gw/api/ntf/push-dvcs/d%2F1");
    expect(init.method).toBe("DELETE");
  });
});
