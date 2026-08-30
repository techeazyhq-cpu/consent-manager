# PEP/PDP Dynamic Enforcement (plan §9, whitepaper Stage 3) — adapted from the whitepaper's
# own example policy to this platform's actual CnsTkn claim shape (sub_did/fiduciary_id/
# purpose_code/access_type/scope/exp, plan §7c step 3, §13).
#
# Phase-1 simplification, documented rather than silent: this policy decodes the CnsTkn
# WITHOUT cryptographic signature verification (io.jwt.decode, not decode_verify) — cm-pdp
# has no JWKS endpoint to fetch cm-cns-cmd-svc's ephemeral-at-boot RSA public key from yet.
# The live_status check below (a call to cm-cns-qry-svc, a trusted internal service) is what
# actually gates the decision, not the token's signature alone. Wiring a JWKS endpoint +
# io.jwt.decode_verify is a Phase 2 hardening item (docs/architecture/adr-0003).
package consent.authorization

import future.keywords.if

default allow := false

allow if {
	input.action == "READ"

	# io.jwt.decode (unverified) returns [header, payload, signature] — no validity boolean,
	# since there's no key here to verify against (see the file-level comment above).
	[_, payload, _] := io.jwt.decode(input.consent_token)
	payload.purpose_code == input.requested_purpose
	payload.access_type == "ALLOW"
	payload.exp > (time.now_ns() / 1000000000)
	live_status == "ISSUED"
}

live_status := status if {
	resp := http.send({
		"method": "GET",
		"url": sprintf("%s/internal/cns-arts/%s/status", [cns_qry_base_url, input.cns_art_id]),
		"headers": {"Accept": "application/json"},
		"timeout": "2s",
	})
	resp.status_code == 200
	status := resp.body.status
}

# Overridable via OPA's own config/data for docker-compose vs local-dev vs test.
cns_qry_base_url := opa_env_cns_qry_base_url if {
	opa_env_cns_qry_base_url := data.config.cns_qry_base_url
} else := "http://cm-cns-qry-svc:8083"
