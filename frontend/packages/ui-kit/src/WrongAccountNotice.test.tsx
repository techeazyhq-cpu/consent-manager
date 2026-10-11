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
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { WrongAccountNotice } from "./index";

afterEach(cleanup);

describe("WrongAccountNotice", () => {
  it("names the account that is signed in and the kind of account the app needs", () => {
    const { container } = render(
      <WrongAccountNotice
        appName="the DP portal"
        requiredAccount="a data principal account"
        username="admin@cm.local"
        onSignOut={() => {}}
      />,
    );

    expect(screen.getByRole("heading", { name: "This account can't use the DP portal" })).toBeTruthy();
    expect(container.textContent)
      .toContain("You're signed in as admin@cm.local, which isn't a data principal account.");
  });

  it("says why it happens: a sign-in from another Consent Manager app in this browser", () => {
    render(<WrongAccountNotice appName="CM Admin" requiredAccount="a platform administrator account"
      username="dp-demo@cm.local" onSignOut={() => {}} />);

    expect(screen.getByText(/another Consent Manager app in this browser/)).toBeTruthy();
  });

  it("still explains itself when the account has no username to show", () => {
    render(<WrongAccountNotice appName="CM Admin" requiredAccount="a platform administrator account"
      onSignOut={() => {}} />);

    expect(screen.getByText(/The account you're signed in with isn't a platform administrator account/))
      .toBeTruthy();
  });

  it("signs out so another account can sign in", () => {
    const onSignOut = vi.fn();
    render(<WrongAccountNotice appName="CM Admin" requiredAccount="a platform administrator account"
      username="dp-demo@cm.local" onSignOut={onSignOut} />);

    fireEvent.click(screen.getByRole("button", { name: "Sign out and use another account" }));

    expect(onSignOut).toHaveBeenCalledOnce();
  });
});
