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

import { describe, expect, it } from "vitest";
import { catcherQuery, cnsReqIdOf, devToken, freshPushes, nextSince, pushModeFor, pushPlatform, stillRegistered, type CaughtPush } from "./push";

describe("the app's push rules", () => {
  const both = { android: true, ios: true };

  it("uses FCM only in a phone build made with that platform's Firebase file", () => {
    expect(pushModeFor({ os: "android", firebase: both, expoGo: false, dev: false })).toBe("fcm");
    expect(pushModeFor({ os: "ios", firebase: both, expoGo: false, dev: true })).toBe("fcm");
    expect(pushModeFor({ os: "ios", firebase: { android: true }, expoGo: false, dev: false })).toBe("off");
    expect(pushModeFor({ os: "android", firebase: {}, expoGo: false, dev: false })).toBe("off");
  });

  it("falls back to development mode in Expo Go, the web preview and development builds without Firebase", () => {
    expect(pushModeFor({ os: "android", firebase: both, expoGo: true, dev: true })).toBe("dev");
    expect(pushModeFor({ os: "web", firebase: both, expoGo: false, dev: true })).toBe("dev");
    expect(pushModeFor({ os: "ios", firebase: {}, expoGo: false, dev: true })).toBe("dev");
    expect(pushModeFor({ os: "web", firebase: both, expoGo: false, dev: false })).toBe("off");
  });

  it("names the platform the way the device registry does", () => {
    expect(pushPlatform("android")).toBe("ANDROID");
    expect(pushPlatform("ios")).toBe("IOS");
    expect(pushPlatform("web")).toBe("WEB");
    expect(devToken("IOS", "123e4567")).toBe("dev-ios-123e4567");
  });

  it("knows whether this phone is still registered", () => {
    expect(stillRegistered("d1", [{ id: "d0" }, { id: "d1" }])).toBe(true);
    expect(stillRegistered("d1", [{ id: "d0" }])).toBe(false);
    expect(stillRegistered(null, [{ id: "d0" }])).toBe(false);
  });

  it("opens a request's screen only for a well-formed request id", () => {
    expect(cnsReqIdOf({ cnsReqId: "0b7c4b0e-5f2a-4d1e-9c3a-7a1b2c3d4e5f" })).toBe("0b7c4b0e-5f2a-4d1e-9c3a-7a1b2c3d4e5f");
    expect(cnsReqIdOf({ messageId: "m-1" })).toBeNull();
    expect(cnsReqIdOf({ cnsReqId: "../account" })).toBeNull();
    expect(cnsReqIdOf({ cnsReqId: 42 })).toBeNull();
    expect(cnsReqIdOf(undefined)).toBeNull();
  });

  describe("the dev push relay", () => {
    const push = (id: string, to: string, receivedAt: string): CaughtPush => ({ id, to, receivedAt, subject: "New consent request" });

    it("asks the catcher for this phone's pushes since a time", () => {
      const url = new URL(catcherQuery("http://10.0.2.2:19000/", "dev-android-1", "2026-10-20T10:00:00.000Z"));
      expect(url.origin + url.pathname).toBe("http://10.0.2.2:19000/messages");
      expect(Object.fromEntries(url.searchParams)).toEqual({ channel: "push", to: "dev-android-1", since: "2026-10-20T10:00:00.000Z" });
    });

    it("shows only this phone's pushes, once each, oldest first", () => {
      const caught = [
        push("b", "dev-ios-1", "2026-10-20T10:00:02.000Z"),
        push("x", "dev-ios-2", "2026-10-20T10:00:01.000Z"),
        push("a", "dev-ios-1", "2026-10-20T10:00:01.000Z"),
        push("c", "dev-ios-1", "2026-10-20T10:00:02.000Z"),
      ];
      expect(freshPushes(caught, "dev-ios-1", new Set(["c"])).map((p) => p.id)).toEqual(["a", "b"]);
    });

    it("moves on to the newest push seen, and stays put when there is none", () => {
      const since = "2026-10-20T10:00:00.000Z";
      expect(nextSince([push("a", "t", "2026-10-20T10:00:05.000Z"), push("b", "t", "2026-10-20T10:00:03.000Z")], since)).toBe(
        "2026-10-20T10:00:05.000Z",
      );
      expect(nextSince([], since)).toBe(since);
    });
  });
});
