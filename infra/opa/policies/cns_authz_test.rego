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
# Unit tests for cns_authz.rego: opa test infra/opa/policies
# The two RSA keys below are TEST-ONLY, generated for these tests and used nowhere else: cm_key stands in for
# cm-cns-cmd-svc's signing key, attacker_key for anyone else's (it even claims the same kid).

package consent.authorization_test

import rego.v1

import data.consent.authorization

cm_key := {"kty":"RSA","n":"w6h4JpjENAU22fDKZpibtmUN3C9Naqt76G95pZ6CxJEVavrYi0HErYDgTd8nLm_JIc28qdD7xbb_SHUe9IVNjkEL8hVR6zjGNN1m5ZBUJXzHP_U6gk1wEywAyWfY3R1ZdJ_Q1Cc5rfdMp6TSXHBHsQcvd-50cDHwVVCqCBTXkg5BYG0UiAZko4Fm2KwlU4-9--0wV4QA0NERe62zvo9GIaVrVAFmpKBwMpgxFysIcKI59TP3ADufgv-IwTL2yF4Y_nZe5JLX7SvwXqT2-2dybnXiBM9eCLGYOinrkZeDfy8hy1kFNOo7k4Odocq6IooAmjY6f_xQB3W-Oxh8F2_87Q","e":"AQAB","d":"B42y9pNL2J6ug4Zv9SFrulE3a92cCJ2BmJnpIXNq1_zdgOmADHKn2nNQ9YcQChM0ICAQHW9qF012_vMELjhEcLsvicaFImaCnGepwWLevl3AixMQ3n2JcWwJXon2tAtq6_1mTTJzrLT3gO3BI0lzmX2BOYkuqWaVwS1uiwZTwv_37iQnjqKUwdzGXGwD4Gljvy0Knfz8xovcFSWgVCkL-hijTJ2KsUSdpALSkNGZJkanHUtU4OignffCG_BmrBEjvjIH7ZAFtuegPnZwvsRqpMS80uD_4J2qtMDhN9EdsG9O_8OrO164LNPXlsLGWHdJPL9ika6MI_FPslAyEFYnYQ","p":"9iCmvQO6_YgoBNJnn-svjW1bh8g--usMGO3wRnr37vo6OloZc6qBv__FouSxFbxJ5pRX_kRW_IFEz3R-tLBFb6RLuXpnND5EPDlABaYxwzvoQsVJc5ILG-KJCNCoYruL3STabG53s2lQHPCIOhYQK-eDDQZmkjXRP11Ic3eNS10","q":"y4GTJ3dzYWVR5KFXXH9JZcrIj3QU5P_7Z14Fj88QBrr4eMy7_raPWnG7HS7eKWVU-kCwXXI7cB94HkJSZN-gJy2mNPtxMa3Rf93ZqkrXqkgTk9AcCJgGlf_8endHyv9V0flDswH3qYzSQVebNKJDHx0kEH_1LINFpNE3X3nJ7tE","dp":"NS242P4FWOIiQ2hym0rla1lFfYD2mqBObNR8Jb5L84Z9Lj5pYWRlxGYu_ZML7_ZOwVMr7_Q0FzqW83voJIIxfn2xHtIjmlYZyClWe-H8voIjHMojpKTZYZ0Anrb2qt56yb08tFDp7AnFFXui82nw9IuA9T98yfp2y4oMLWaXUME","dq":"OyxCDYe-FOPRHBmNvWeFgAOmrY-jQ7Lq9vNvypD6p_pVpUpin32V0Uagkau_DWpZeyq0HXfJsKB13NaL_qjHeJzdZyp1j2xrLjLjx5BbhTfP-XrZq-YIhDUaxMFKLsxvt84y0SoRSUuZF0bR6kwEGFfw1JtgPsEszu_GmgxfmwE","qi":"V0-nxWjvbBUQhjHASaESPfwLUFkHkWPIZL6uBGm1LKvkvVZnWtIfDdEEd-IkXyvESQ1W96pZG6vCOpDTc82Qj-ia_u81RN6x0uM27V6o_SpqZ5EpJ_aQKesS4CvGPZeJfNjpbShi1UkJ93IxfL9JLLnan6cAahoq1HelwGzUO7E","kid":"test-cm-key","alg":"RS256","use":"sig"}

attacker_key := {"kty":"RSA","n":"poBjnGpEYTeWugj103pC-tIrwupBhELi2dhgt7QFT8RCja6LnftPUl-ETumsJa38mdb_eJUHeOf_Qn42fcNJ51dzjBl3_Q8_zTPmSsEqwUf-vIaVTFErreYDuSBjzeVxONA3IjhaevzFwnZyG4k3eTneXmzuzqTdE682sBVRvESKzxmKTyo5vDNFdux5-1Q50y-1ao6i4Bm_3UGG1cqft8nNowqpcMLB-SlxwO7VJSL6H4ws34dVQV1phRwWnfu99Tl9Dcln_bIPEr2rBWaVI45KkR85BFLHnaO669sAy7A8p4NGiTQDkICstLQy-UFSOMAhv-Hi2707e4qSCwzpWw","e":"AQAB","d":"AREF5JqF8Aie5-VwFRfpCFnU_8jVoZlSFIYjyiu69B9YxhKN7j7ExzsU983cXn7Yl2_1eIfms9oO3CgaYIlrMftexuN03_EpIogjyWs5MPUI4puK7_0iitjUHSI9eo-BLRV8WFmyfGLCQBiy1_wqqgMxPKji95BQWVkkwCz9Ea7aoBxFFoblwwdTpezxgcuxdEsP_ZSfaGq3jTekeMk10UWZxuKO6mw1udLS0rEgbDQ6FrFIxuNeu8qOMShenw0Oaan4ugumTKb4gDBUDz_CnUvpFW2ox1dur1rhgm_y1xVTu19MuNgpLLR-J7rsaBYVHcl8GPsuP6uYzZ35L3DXmQ","p":"48qWaAwK2zDm22T4LZKvcEMvxriGQ62YxzUMk_3ONMw_MaSi1ZaVE8-RLrUYsRp_ldrlYvUsH3WHTX7diqkn7w6hZXvafYqrGmePSYHgEbiUvoH17DvGk_QUHVSlaucZSrXPm87xuIv8s4ra2dcXXEN1E8XJj3d2y_Yfaky38Ik","q":"ux7Mip_Th7WgxMgbwtUSfgnTamebetViXjwPgKyIfGX-csBj2r8NI0Yjx_sYwr7XIuQG0BU3asDmoPgX6dUNHHoSCeHEMPZpnLYs4Q2AaT9bPL-Pc6NZhplRDzfLsnLXLXSql7D3Dc-ef80QBO0-4GN7IK9OxAprLC2boH_k6cM","dp":"mU1cUAXawI12sKyRQcp4e6sb2jeRVLbeHSMcpfSn9GyJExXj6K8BAWYuJSsHxRk6SFYbDrBF0547QvJgktMo5wAHqs7hyp-qa4jKrAPWEtcQc-3u3d-U0znu15aCBSImqVsWr5tknnjHxuVqSDUzqYpUnsv7zKvvrXCkz9t151E","dq":"JbhIrS0YvAOd--0Gw5imGKgsaQenrPQ01yAuU9bz0HfS-hjUSDU5FcI-1kJEtIU54vuqb-nG-QsXsp0wXN-ou_PTjRBotyIFGiUTGO7a0tyFrNJEvZ_RHTPsxi-OLVO_EpexB9ERjEQtBEA3GXcltJ_5IxaTvysGfapX89u0Lnc","qi":"M4ZsiRmylBvrK_EaYikAJO_2mEK9b4OkAU4bxHFEepyx8fqCYptyQfuhs6xs7tFjEZHe8HVGC-JE6cesO3XbjjtlJ1h_Qkfzos1Whq0lJzMfQtF4TsMG4Ys7NrdSE5zYUZ9ntYJ7-goStRayrKgSKQlBtoX3eLODGHLH7wIyAWQ","kid":"test-cm-key","alg":"RS256","use":"sig"}

published_jwks := "{\"keys\":[{\"kty\":\"RSA\",\"n\":\"w6h4JpjENAU22fDKZpibtmUN3C9Naqt76G95pZ6CxJEVavrYi0HErYDgTd8nLm_JIc28qdD7xbb_SHUe9IVNjkEL8hVR6zjGNN1m5ZBUJXzHP_U6gk1wEywAyWfY3R1ZdJ_Q1Cc5rfdMp6TSXHBHsQcvd-50cDHwVVCqCBTXkg5BYG0UiAZko4Fm2KwlU4-9--0wV4QA0NERe62zvo9GIaVrVAFmpKBwMpgxFysIcKI59TP3ADufgv-IwTL2yF4Y_nZe5JLX7SvwXqT2-2dybnXiBM9eCLGYOinrkZeDfy8hy1kFNOo7k4Odocq6IooAmjY6f_xQB3W-Oxh8F2_87Q\",\"e\":\"AQAB\",\"kid\":\"test-cm-key\",\"alg\":\"RS256\",\"use\":\"sig\"}]}"

mock_http_send(request) := {"status_code": 200, "raw_body": published_jwks} if endswith(request.url, "/.well-known/jwks.json")

mock_http_send(request) := {"status_code": 200, "body": {"status": "ISSUED"}} if endswith(request.url, "/internal/cns-arts/art-1/status")

mock_http_send(request) := {"status_code": 200, "body": {"status": "REVOKED"}} if endswith(request.url, "/internal/cns-arts/art-revoked/status")

no_keys_http_send(request) := {"status_code": 503, "raw_body": ""} if endswith(request.url, "/.well-known/jwks.json")

no_keys_http_send(request) := {"status_code": 200, "body": {"status": "ISSUED"}} if contains(request.url, "/internal/cns-arts/")

in_one_hour := (time.now_ns() / 1000000000) + 3600

claims(overrides) := object.union(
	{
		"iss": "cm-cns-cmd-svc",
		"cns_art_id": "art-1",
		"sub_did": "dp-1",
		"fiduciary_id": "df-1",
		"purpose_code": "KYC",
		"access_type": "ALLOW",
		"scope": [],
		"exp": in_one_hour,
	},
	overrides,
)

token(overrides) := io.jwt.encode_sign({"alg": "RS256", "kid": "test-cm-key"}, claims(overrides), cm_key)

request(consent_token, overrides) := object.union(
	{
		"action": "READ",
		"cns_art_id": "art-1",
		"consent_token": consent_token,
		"caller_df_id": "df-1",
		"requested_purpose": "KYC",
		"requested_attribute": "",
	},
	overrides,
)

decide(consent_token, overrides) if {
	authorization.allow with input as request(consent_token, overrides)
		with http.send as mock_http_send
}

test_a_genuine_token_for_this_artefact_fiduciary_and_purpose_is_allowed if {
	decide(token({}), {})
}

test_a_token_signed_with_any_other_key_is_denied_even_with_the_right_kid if {
	forged := io.jwt.encode_sign({"alg": "RS256", "kid": "test-cm-key"}, claims({}), attacker_key)
	not decide(forged, {})
}

test_an_unsigned_or_symmetrically_signed_token_is_denied if {
	hs256 := io.jwt.encode_sign({"alg": "HS256", "kid": "test-cm-key"}, claims({}), {"kty": "oct", "k": "c2VjcmV0"})
	not decide(hs256, {})
	not decide("eyJhbGciOiJub25lIn0.e30.", {})
}

test_a_token_is_bound_to_its_artefact if {
	not decide(token({}), {"cns_art_id": "art-revoked"})
	not decide(token({"cns_art_id": "art-2"}), {})
}

test_a_token_is_only_honoured_for_the_fiduciary_it_names if {
	not decide(token({}), {"caller_df_id": "df-2"})
	not decide(token({}), {"caller_df_id": ""})
}

test_the_purpose_must_match_and_be_allowed if {
	not decide(token({}), {"requested_purpose": "MARKETING"})
	not decide(token({"access_type": "DENY"}), {})
}

test_an_expired_token_or_one_from_another_issuer_is_denied if {
	not decide(token({"exp": (time.now_ns() / 1000000000) - 60}), {})
	not decide(token({"iss": "someone-else"}), {})
}

test_a_revoked_artefact_is_denied_even_with_a_genuine_token if {
	revoked_token := token({"cns_art_id": "art-revoked"})
	not decide(revoked_token, {"cns_art_id": "art-revoked"})
}

test_without_the_signing_keys_everything_is_denied if {
	not authorization.allow with input as request(token({}), {})
		with http.send as no_keys_http_send
}
