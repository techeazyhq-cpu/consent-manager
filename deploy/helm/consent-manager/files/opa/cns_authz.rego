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
# PEP/PDP dynamic enforcement (ADR-006): cm-gw asks whether a fiduciary may act on a consent token. Allowed only when
#   - the token's signature verifies against cm-cns-cmd-svc's published keys, its issuer is cm-cns-cmd-svc and it
#     has not expired;
#   - it was issued for this artefact (cns_art_id) and to the calling fiduciary (fiduciary_id = the caller's df_id);
#   - it grants (ALLOW) the purpose asked for;
#   - and the artefact is still ISSUED right now, as recorded by cm-cns-cmd-svc, the write side: a revocation or a
#     lapse takes effect on the very next check (ADR-027). The read model lags a revoke, so it never decides this.
# Anything missing or unreachable denies: the keys, the live status, the caller's identity.

package consent.authorization

import rego.v1

default allow := false

allow if {
	input.action == "READ"
	input.caller_df_id != ""
	[valid, _, token] := io.jwt.decode_verify(input.consent_token, {
		"cert": signing_keys,
		"iss": "cm-cns-cmd-svc",
		"time": time.now_ns(),
	})
	valid
	token.cns_art_id == input.cns_art_id
	token.fiduciary_id == input.caller_df_id
	token.purpose_code == input.requested_purpose
	token.access_type == "ALLOW"
	live_status == "ISSUED"
}

# The public JSON Web Key Set cm-cns-cmd-svc signs consent tokens with, cached for five minutes. Put a new key into
# the set at least that long before it starts signing.
signing_keys := resp.raw_body if {
	resp := http.send({
		"method": "GET",
		"url": sprintf("%s/api/cns-cmd/.well-known/jwks.json", [cns_cmd_base_url]),
		"timeout": "2s",
		"force_cache": true,
		"force_cache_duration_seconds": 300,
	})
	resp.status_code == 200
}

live_status := status if {
	resp := http.send({
		"method": "GET",
		"url": sprintf("%s/internal/cns-arts/%s/status", [cns_cmd_base_url, input.cns_art_id]),
		"headers": {"Accept": "application/json"},
		"timeout": "2s",
	})
	resp.status_code == 200
	status := resp.body.status
}

# Service addresses, overridable through OPA data for other environments.
cns_cmd_base_url := data.config.cns_cmd_base_url if data.config.cns_cmd_base_url
else := "http://cm-cns-cmd-svc:8082"
