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
# Anything missing or unreachable is never an allow. But "couldn't check" is not "not allowed" (ADR-057, VAPT V-17):
# when the keys or the live status can't be had, `undetermined` says so, and the PEP answers 503, not DENY.
# cm-gw asks for `decision`, which holds both.

package consent.authorization

import rego.v1

default allow := false

allow if {
	token_grants
	live_status == "ISSUED"
}

# Couldn't decide: never an allow, but no decision either. Only for a request that a definite deny doesn't settle
# first: without the keys a token can't be checked at all, and the live status matters only to a token that grants.
default undetermined := false

undetermined if {
	well_formed
	keys_unavailable
}

undetermined if {
	token_grants
	status_unavailable
}

decision := {"allow": allow, "undetermined": undetermined}

well_formed if {
	input.action == "READ"
	input.caller_df_id != ""
}

# The token is genuine, in date, and grants this fiduciary this purpose on this artefact; the live status is apart.
token_grants if {
	well_formed
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
}

# The public JSON Web Key Set cm-cns-cmd-svc signs consent tokens with, cached for five minutes. Put a new key into
# the set at least that long before it starts signing. raise_error false: a timeout or a refused connection comes
# back as status 0 with an error, instead of leaving the rule undefined, so it can be told apart from a deny.
jwks_resp := http.send({
	"method": "GET",
	"url": sprintf("%s/api/cns-cmd/.well-known/jwks.json", [cns_cmd_base_url]),
	"timeout": "2s",
	"force_cache": true,
	"force_cache_duration_seconds": 300,
	"raise_error": false,
})

signing_keys := jwks_resp.raw_body if jwks_resp.status_code == 200

# Any answer but the key set: without it no token can be checked, whatever the reason.
keys_unavailable if jwks_resp.status_code != 200

status_resp := http.send({
	"method": "GET",
	"url": sprintf("%s/internal/cns-arts/%s/status", [cns_cmd_base_url, input.cns_art_id]),
	"headers": {"Accept": "application/json"},
	"timeout": "2s",
	"raise_error": false,
})

live_status := status_resp.body.status if status_resp.status_code == 200

# Unreachable or timed out (status 0), or unhealthy (5xx, 429), as cm-res-kit's RemoteFailures counts it. A 404 is an
# answer: no such artefact, a deny.
status_unavailable if not status_answered

status_answered if {
	status_resp.status_code >= 200
	status_resp.status_code < 500
	status_resp.status_code != 429
}

# Service addresses, overridable through OPA data for other environments.
cns_cmd_base_url := data.config.cns_cmd_base_url if data.config.cns_cmd_base_url
else := "http://cm-cns-cmd-svc:8082"
