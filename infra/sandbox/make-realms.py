# Copyright 2026 Vasantha Kumar
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# @author Vasantha Kumar <vasantha.kumar@hotmail.com>

"""Writes the sandbox's realms (sbx-cm-dp, sbx-cm-df, sbx-cm-staff) from production's, so they never drift apart.

ADR-037: the same realms with a prefix. Keycloak generates each realm's signing keys when it first imports it (the
exports hold none), so a sandbox token never verifies in production. Codes are sent through the sandbox's cm-ntf-svc,
whose mail stays in the sandbox's inbox. Seeded people use test addresses at sandbox.example.

Run after changing a production realm export, and commit the result:  python infra/sandbox/make-realms.py
"""

import json
import pathlib

EXPORTS = pathlib.Path(__file__).resolve().parent.parent / "keycloak" / "realm-export"
PREFIX = "sbx-"
SANDBOX_NTF = "http://cm-sbx-ntf-svc:8084"


def sandboxed(realm: dict) -> dict:
    realm = json.loads(json.dumps(realm))
    realm["realm"] = PREFIX + realm["realm"]
    realm["displayName"] = f"{realm.get('displayName', realm['realm'])} (sandbox)"
    for config in realm.get("authenticatorConfig", []):
        if "ntfBaseUrl" in config.get("config", {}):
            config["config"]["ntfBaseUrl"] = SANDBOX_NTF
    for user in realm.get("users", []):
        for field in ("username", "email"):
            if user.get(field, "").endswith("@cm.local"):
                user[field] = user[field].replace("@cm.local", "@sandbox.example")
    return realm


def main() -> None:
    for source in sorted(EXPORTS.glob("cm-*.json")):
        target = EXPORTS / (PREFIX + source.name)
        realm = sandboxed(json.loads(source.read_text(encoding="utf-8")))
        target.write_text(json.dumps(realm, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        print(f"{source.name} -> {target.name}")


if __name__ == "__main__":
    main()
