#!/usr/bin/env bash
# Scripted end-to-end curl flow (plan §16) exercising the full consent lifecycle through
# cm-gw. Uses Keycloak password-grant login for the seeded demo users since cm-idn-otp-spi
# (passwordless OTP, plan §9a) doesn't exist yet — see cm-realm.json's _comment.
set -euo pipefail

KC_BASE="http://localhost:8080"
GW_BASE="http://localhost:9000"
REALM="cm-realm"
CLIENT="cm-shell"

jget() { node -e "let d=''; process.stdin.on('data',c=>d+=c); process.stdin.on('end',()=>{try{console.log(JSON.parse(d)$1)}catch(e){console.error('PARSE FAIL:',d);process.exit(1)}})"; }

echo "== 1. CM_ADMIN password-grant login =="
ADMIN_TOKEN=$(curl -s -X POST "$KC_BASE/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=$CLIENT&username=admin@cm.local&password=admin" | jget '.access_token')
echo "  got admin token (${#ADMIN_TOKEN} chars)"

echo "== 2. Register DF =="
DF_RESP=$(curl -s -X POST "$GW_BASE/api/df-reg/dfs" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Acme Retail","contactEmail":"acme@example.com"}')
echo "  $DF_RESP"
DF_ID=$(echo "$DF_RESP" | jget '.id')
echo "  dfId=$DF_ID"

echo "== 3. Activate DF (provisions cm-df-api client secret) =="
ACTIVATE_RESP=$(curl -s -X POST "$GW_BASE/api/df-reg/dfs/$DF_ID/activate" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "  $ACTIVATE_RESP"
DF_SECRET=$(echo "$ACTIVATE_RESP" | jget '.cmDfApiClientSecret')
echo "  got client secret (${#DF_SECRET} chars)"

echo "== 4. DF Integration API: client-credentials login as df-api-$DF_ID =="
DF_TOKEN=$(curl -s -X POST "$KC_BASE/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=client_credentials&client_id=df-api-$DF_ID&client_secret=$DF_SECRET" | jget '.access_token')
echo "  got DF token (${#DF_TOKEN} chars)"

echo "== 5. DF creates CnsReq for an UNLINKED DP (by email, no dpId yet) =="
CNSREQ_RESP=$(curl -s -X POST "$GW_BASE/api/cns-cmd/cns-reqs" \
  -H "Authorization: Bearer $DF_TOKEN" -H "Content-Type: application/json" \
  -d '{"extDpRefTyp":"EMAIL","extDpRefVal":"dp-demo@cm.local","prpsCode":"ORDER_FULFILLMENT","dCatCodes":["EMAIL","NAME"],"ntcTitle":"Consent for order fulfillment","ntcText":"We need your email and name to fulfill your order."}')
echo "  $CNSREQ_RESP"
CNS_REQ_ID=$(echo "$CNSREQ_RESP" | jget '.id')
echo "  cnsReqId=$CNS_REQ_ID"

echo "== 6. DP password-grant login =="
DP_TOKEN=$(curl -s -X POST "$KC_BASE/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=$CLIENT&username=dp-demo@cm.local&password=dp-demo" | jget '.access_token')
echo "  got DP token (${#DP_TOKEN} chars)"
DP_SUB=$(echo "$DP_TOKEN" | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const p=JSON.parse(Buffer.from(d.split('.')[1],'base64url').toString());console.log(p.sub)})")
echo "  dpSub=$DP_SUB"

echo "== 7. DP links their pre-existing unlinked CnsReq (plan §7a — simulating post-signup auto-link) =="
LINK_RESP=$(curl -s -X POST "$GW_BASE/api/cns-cmd/dp-links" \
  -H "Authorization: Bearer $DP_TOKEN" -H "Content-Type: application/json" \
  -d '{"extDpRefTyp":"EMAIL","extDpRefVal":"dp-demo@cm.local"}')
echo "  $LINK_RESP"

echo "== 8. Poll read model until the projector picks it up =="
for i in $(seq 1 15); do
  LIST_RESP=$(curl -s "$GW_BASE/api/cns-qry/cns-arts?dpId=$DP_SUB" -H "Authorization: Bearer $DP_TOKEN")
  COUNT=$(echo "$LIST_RESP" | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d).length))" 2>/dev/null || echo 0)
  if [ "$COUNT" -gt 0 ]; then echo "  projected: $LIST_RESP"; break; fi
  echo "  (attempt $i) not yet projected, waiting..."
  sleep 2
done

echo "== 9. DP approves the request =="
APPROVE_RESP=$(curl -s -X POST "$GW_BASE/api/cns-cmd/cns-reqs/$CNS_REQ_ID/approve" -H "Authorization: Bearer $DP_TOKEN")
echo "  $APPROVE_RESP"
CNS_ART_ID=$(echo "$APPROVE_RESP" | jget '.id')
CNS_TKN=$(echo "$APPROVE_RESP" | jget '.cnsTkn')
echo "  cnsArtId=$CNS_ART_ID"

echo "== 10. PEP/PDP check: matching purpose -> expect ALLOW (200) =="
curl -s -o /dev/null -w "  HTTP %{http_code}\n" "$GW_BASE/api/cns-qry/cns-arts/$CNS_ART_ID/verify?purpose=ORDER_FULFILLMENT" \
  -H "Authorization: Bearer $DF_TOKEN" -H "X-Cns-Tkn: $CNS_TKN"

echo "== 11. PEP/PDP check: mismatched purpose -> expect DENY (403) =="
curl -s -o /dev/null -w "  HTTP %{http_code}\n" "$GW_BASE/api/cns-qry/cns-arts/$CNS_ART_ID/verify?purpose=AI_TRAINING" \
  -H "Authorization: Bearer $DF_TOKEN" -H "X-Cns-Tkn: $CNS_TKN"

echo "== 12. Revoke =="
curl -s -X POST "$GW_BASE/api/cns-cmd/cns-arts/$CNS_ART_ID/revoke" -H "Authorization: Bearer $DP_TOKEN"
echo

echo "== 13. Re-check after revoke -> expect DENY (403), proving live status check, not just token exp =="
curl -s -o /dev/null -w "  HTTP %{http_code}\n" "$GW_BASE/api/cns-qry/cns-arts/$CNS_ART_ID/verify?purpose=ORDER_FULFILLMENT" \
  -H "Authorization: Bearer $DF_TOKEN" -H "X-Cns-Tkn: $CNS_TKN"

echo "== 14. Billing: usage + eligibility =="
curl -s "$GW_BASE/api/bil/dfs/$DF_ID/usage" -H "Authorization: Bearer $ADMIN_TOKEN"; echo

echo "== 15. Mailpit: confirm the consent-initiated notification was sent =="
curl -s "http://localhost:8025/api/v1/messages" | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const m=JSON.parse(d).messages||[];console.log('  '+m.length+' message(s) captured; latest subject: '+(m[0]?m[0].Subject:'(none)'))})"

echo
echo "Done."
