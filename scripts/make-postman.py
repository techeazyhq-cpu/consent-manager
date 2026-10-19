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

"""Writes the Postman collection and environments for the Data Fiduciary API from its OpenAPI description.

    python scripts/make-postman.py

Every request authenticates with the API key in {{apiKey}} and is signed (CM1-HMAC-SHA256, ADR-041) by the
collection's pre-request script with {{signingSecret}}, a fresh timestamp and a fresh nonce. Ids that later requests
need ({{requestId}}, {{batchId}}) are captured from responses. The Purposes folder comes first: requests name the
purpose version it defines and publishes (ADR-031), and run again it accepts that the purpose already exists. A "Replay protection" folder sends one signed request
twice and expects the second to be refused.

Postman builds multipart bodies after pre-request scripts run, so the CSV upload goes unsigned: use it with a key that
doesn't require signing, or sign uploads from code as the API guide shows.
"""

import json
import pathlib
import uuid

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "api" / "df-api.openapi.yaml"
OUT = ROOT / "docs" / "api"

SIGN = r"""// CM1-HMAC-SHA256 (ADR-041): sign the method, path, query and exact body with a fresh timestamp and nonce.
const cryptojs = require('crypto-js');
const secret = pm.variables.get('signingSecret');
pm.request.headers.upsert({ key: 'X-API-Key', value: pm.variables.replaceIn('{{apiKey}}') });
if (!secret || pm.request.body && pm.request.body.mode === 'formdata') {
  // no secret, or a multipart upload Postman builds after this script: sent unsigned
  ['X-Signature', 'X-Signature-Timestamp', 'X-Signature-Nonce'].forEach(h => pm.request.headers.remove(h));
  return;
}
const offset = Number(pm.collectionVariables.get('timestampOffset') || 0); // the replay demo backdates one request
const timestamp = (Math.floor(Date.now() / 1000) + offset).toString();
const fixed = pm.collectionVariables.get('fixedNonce'); // the replay demo reuses one nonce
const nonce = fixed || (cryptojs.lib.WordArray.random(16).toString(cryptojs.enc.Hex));
const method = pm.request.method.toUpperCase();
const path = '/' + pm.request.url.path.map(p => pm.variables.replaceIn(p)).join('/');
const query = pm.request.url.query.filter(q => !q.disabled)
  .map(q => q.key + (q.value === null || q.value === undefined ? '' : '=' + pm.variables.replaceIn(q.value))).join('&');
const body = pm.request.body && pm.request.body.mode === 'raw' ? pm.variables.replaceIn(pm.request.body.raw) : '';
if (pm.request.body && pm.request.body.mode === 'raw') {
  pm.request.body.update(body); // send exactly the bytes signed
}
const canonical = ['CM1-HMAC-SHA256', timestamp, nonce, method, path, query, cryptojs.SHA256(body).toString(cryptojs.enc.Hex)].join('\n');
pm.request.headers.upsert({ key: 'X-Signature-Timestamp', value: timestamp });
pm.request.headers.upsert({ key: 'X-Signature-Nonce', value: nonce });
pm.request.headers.upsert({ key: 'X-Signature', value: cryptojs.HmacSHA256(canonical, secret).toString(cryptojs.enc.Hex) });
"""

CAPTURE = {
    # run again, the purpose is already defined, and already published: both are fine
    "definePurpose": "pm.test('defined, or already was', () => pm.expect(pm.response.code).to.be.oneOf([201, 409]));\n"
                     "if (pm.response.code === 201) pm.collectionVariables.set('prpsCode', pm.response.json().code);",
    "publishPurposeVersion": "pm.test('published, or nothing left to publish', () => "
                             "pm.expect(pm.response.code).to.be.oneOf([200, 409]));",
    "raiseConsentRequest": "pm.test('raised', () => pm.response.to.have.status(201));\n"
                           "pm.collectionVariables.set('requestId', pm.response.json().id);",
    "sendToMany": "pm.test('accepted or checked', () => pm.expect(pm.response.code).to.be.oneOf([200, 202]));\n"
                  "const b = pm.response.json().batch; if (b && pm.response.code === 202) pm.collectionVariables.set('batchId', b.id);",
    "listConsentRequests": "pm.test('ok', () => pm.response.to.have.status(200));\n"
                           "const first = pm.response.json()[0]; if (first) { pm.collectionVariables.set('requestId', first.cnsReqId);"
                           " if (first.cnsArtId) pm.collectionVariables.set('cnsArtId', first.cnsArtId); }",
}
DEFAULT_TEST = "pm.test('succeeds', () => pm.expect(pm.response.code).to.be.below(300));"

VARIABLES = {"requestId": "", "batchId": "", "cnsArtId": "", "cnsTkn": "", "purpose": "ORDER_UPDATES",
             "prpsCode": "ORDER_UPDATES",
             "fixedNonce": "", "timestampOffset": "0"}


def path_segments(path: str):
    return [("{{" + s[1:-1] + "}}") if s.startswith("{") else s for s in path.strip("/").split("/")]


def item(path: str, method: str, op: dict) -> dict:
    query = []
    headers = []
    for p in op.get("parameters", []):
        if "$ref" in p:
            continue
        if p["in"] == "query":
            example = p.get("schema", {}).get("example") or p.get("schema", {}).get("default")
            value = "{{purpose}}" if p["name"] == "purpose" else ("" if example is None else str(example).lower()
                                                                 if isinstance(example, bool) else str(example))
            query.append({"key": p["name"], "value": value, "disabled": not p.get("required", False)
                          and p["name"] not in ("dryRun",), "description": p.get("description", "")})
        if p["in"] == "header" and p["name"] == "X-Cns-Tkn":
            headers.append({"key": "X-Cns-Tkn", "value": "{{cnsTkn}}"})
    request = {
        "method": method.upper(),
        "header": headers,
        "url": {
            "raw": "{{baseUrl}}" + "".join("/" + s for s in path_segments(path))
                   + ("?" + "&".join(q["key"] + "=" + q["value"] for q in query if not q["disabled"]) if any(not q["disabled"] for q in query) else ""),
            "host": ["{{baseUrl}}"],
            "path": path_segments(path),
            "query": query,
        },
        "description": (op.get("description") or op.get("summary", "")).strip(),
    }
    if op.get("security") == []:
        request["auth"] = {"type": "noauth"}
    body = op.get("requestBody", {}).get("content", {})
    if "application/json" in body:
        example = body["application/json"].get("example", {})
        request["header"].append({"key": "Content-Type", "value": "application/json"})
        request["body"] = {"mode": "raw", "raw": json.dumps(example, indent=2), "options": {"raw": {"language": "json"}}}
    test = CAPTURE.get(op["operationId"], DEFAULT_TEST)
    ids = [seg[2:-2] for seg in path_segments(path) if seg.startswith("{{")]
    if ids and op["operationId"] not in CAPTURE:
        # needs an id an earlier request captures (a granted consent's, say): skipped until there is one
        need = " && ".join(f"pm.collectionVariables.get('{i}')" for i in ids)
        test = (f"if ({need}) {{ {DEFAULT_TEST} }}\n"
                f"else {{ pm.test.skip('needs {', '.join(ids)} from an earlier request', () => {{}}); }}")
    events = [{"listen": "test", "script": {"type": "text/javascript", "exec": test.split("\n")}}]
    return {"name": op.get("summary", op["operationId"]), "request": request, "event": events}


def upload_item() -> dict:
    csv = "Email,Customer ID\nasha@sandbox.example,CUST-001\nravi@sandbox.example,CUST-002\n"
    return {
        "name": "Send a CSV list (unsigned upload)",
        "request": {
            "method": "POST",
            "header": [],
            "url": {"raw": "{{baseUrl}}/api/cns-cmd/cns-req-batches?dryRun=true", "host": ["{{baseUrl}}"],
                    "path": ["api", "cns-cmd", "cns-req-batches"], "query": [{"key": "dryRun", "value": "true"}]},
            "body": {"mode": "formdata", "formdata": [
                {"key": "prpsCode", "value": "ORDER_UPDATES", "type": "text"},
                {"key": "prpsVer", "value": "1", "type": "text"},
                {"key": "file", "type": "file", "src": "list.csv",
                 "description": "Choose a CSV such as:\n" + csv},
            ]},
            "description": "Postman builds multipart bodies after the signing script runs, so this goes unsigned: "
                           "a key that requires signing refuses it (signature-required). Sign uploads from code.",
        },
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
            "// with a key that requires signing, an unsigned upload is refused, as it should be",
            "pm.test('checked, or refused for want of a signature', () => {",
            "  if (pm.response.code === 401) pm.expect(pm.response.json().title).to.eql('signature-required');",
            "  else pm.expect(pm.response.code).to.eql(200);",
            "});"]}}],
    }


def replay_folder(spec: dict) -> dict:
    """Postman signs in the collection's pre-request script, before a request's own: each step sets up the next."""
    op = spec["paths"]["/api/cns-cmd/cns-reqs"]["post"]

    def step(name, tests):
        it = item("/api/cns-cmd/cns-reqs", "post", op)
        it["name"] = name
        it["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": tests}}]
        return it

    choose = item("/api/bil/me/subscription", "get", spec["paths"]["/api/bil/me/subscription"]["get"])
    choose["name"] = "0. Choose a nonce for the next request"
    choose["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "pm.collectionVariables.set('fixedNonce', 'replay-' + Date.now() + '-' + Math.floor(Math.random() * 1e6));"]}}]
    return {"name": "Replay protection",
            "description": "ADR-041's guarantees: a signed request is accepted once, within five minutes of its timestamp.",
            "item": [
                choose,
                step("1. A signed request is accepted", [
                    "pm.test('first use is accepted', () => pm.response.to.have.status(201));"]),
                step("2. The same nonce again is a replay", [
                    "pm.test('refused as a replay', () => pm.response.to.have.status(409));",
                    "pm.test('says why', () => pm.expect(pm.response.json().title).to.eql('request-replayed'));",
                    "pm.collectionVariables.set('fixedNonce', '');",
                    "pm.collectionVariables.set('timestampOffset', '-600');"]),
                step("3. A signature ten minutes old has expired", [
                    "pm.test('refused as expired', () => pm.response.to.have.status(401));",
                    "pm.test('says why', () => pm.expect(pm.response.json().title).to.eql('signature-expired'));",
                    "pm.collectionVariables.set('timestampOffset', '0');"]),
            ]}


def main() -> None:
    spec = yaml.safe_load(SPEC.read_text(encoding="utf-8"))
    folders: dict[str, list] = {}
    for path, ops in spec["paths"].items():
        for method, op in ops.items():
            folders.setdefault(op["tags"][0], []).append(item(path, method, op))
    folders["Batches"].insert(1, upload_item())
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "consent-manager/df-api")),
            "name": spec["info"]["title"],
            "description": spec["info"]["description"],
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {"type": "noauth"},
        "event": [{"listen": "prerequest", "script": {"type": "text/javascript", "exec": SIGN.strip().split("\n")}}],
        "variable": [{"key": k, "value": v} for k, v in VARIABLES.items()],
        "item": [{"name": tag["name"], "description": tag.get("description", ""), "item": folders[tag["name"]]}
                 for tag in spec["tags"] if tag["name"] in folders] + [replay_folder(spec)],
    }
    (OUT / "df-api.postman_collection.json").write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8")
    for name, base in [("local", "http://localhost:9000"), ("sandbox", "http://localhost:9100")]:
        env = {
            "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "consent-manager/df-api/" + name)),
            "name": f"Consent Manager — {name}",
            "values": [
                {"key": "baseUrl", "value": base, "enabled": True},
                {"key": "apiKey", "value": "", "type": "secret", "enabled": True},
                {"key": "signingSecret", "value": "", "type": "secret", "enabled": True},
            ],
            "_postman_variable_scope": "environment",
        }
        (OUT / f"df-api.{name}.postman_environment.json").write_text(json.dumps(env, indent=2) + "\n", encoding="utf-8")
    print("wrote", OUT / "df-api.postman_collection.json")


if __name__ == "__main__":
    main()
