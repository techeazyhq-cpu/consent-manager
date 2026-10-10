# Verifying the audit ledger

consent-manager records every platform event in a tamper-evident ledger ([ADR-014](adr/adr-014-tamper-evident-audit-ledger.md))
and has an external timestamp authority sign periodic checkpoints of it
([ADR-015](adr/adr-015-ledger-checkpoints-anchored-by-a-timestamp-authority.md)). This page is for anyone who wants to
check it without trusting consent-manager: a data principal, a fiduciary, an auditor or the Data Protection Board.

## What you can check

| Question | How |
|---|---|
| Has anything recorded been changed, removed or reordered? | `GET /api/aud/chain/verify` (administrators), or recompute the chain from the entries yourself |
| Was the ledger rewritten after the fact, even by its operator? | The checkpoints: each one's hash is signed by an external RFC 3161 authority at the time |
| Is this particular event part of the ledger as it was timestamped? | `GET /api/aud/entries/{seq}/proof`, checked offline as below |

## Checking one entry offline

Ask for the proof of an entry you are entitled to see (your own as a principal, your organisation's as a fiduciary):

```bash
curl -s https://<gateway>/api/aud/entries/42/proof -H "Authorization: Bearer $TOKEN" > proof.json
```

It contains the `entry`, its `merkle` path (`leaf`, `path`, `root`), the `checkpoint` and the `anchor` (the
authority's URL, the time it gives, and the token as `tokenBase64`). Check three things.

**1. The entry's hash.** SHA-256, as lower-case hex, of these lines joined by a line feed, with no line feed at the end
(every value exactly as it appears in the JSON: times are ISO-8601 in UTC, at most to the microsecond, without trailing
zeros; an absent id is an empty line):

```text
cm-aud-v1
<seq>
<evtId>
<evtType>
<occurredAt>
<recordedAt>
<cnsReqId>
<cnsArtId>
<dfId>
<payloadSha256>
<prevHash>
```

It must equal `entry.entryHash`, which is also `merkle.leaf`.

**2. The path to the checkpoint.** RFC 6962 hashing: start from `SHA-256(0x00 || leaf)`; for each step, combine with the
sibling as `SHA-256(0x01 || left || right)`, the sibling on the left when `siblingOnLeft` is true. The result must equal
`merkle.root`, which must equal `checkpoint.merkleRoot`. Then the checkpoint's own hash, SHA-256 of

```text
cm-aud-checkpoint-v1
<id>
<fromSeq>
<toSeq>
<merkleRoot>
<headHash>
<prevCheckpointHash>
<createdAt>
```

must equal `checkpoint.checkpointHash` (values again exactly as in the JSON). In Node.js:

```js
const crypto = require("crypto");
const p = JSON.parse(require("fs").readFileSync("proof.json", "utf8"));
const sha = (...parts) => crypto.createHash("sha256").update(Buffer.concat(parts)).digest();
let node = sha(Buffer.from([0]), Buffer.from(p.merkle.leaf, "hex"));
for (const step of p.merkle.path) {
  const sibling = Buffer.from(step.hash, "hex");
  node = step.siblingOnLeft ? sha(Buffer.from([1]), sibling, node) : sha(Buffer.from([1]), node, sibling);
}
const c = p.checkpoint;
const cpHash = crypto.createHash("sha256")
  .update(["cm-aud-checkpoint-v1", c.id, c.fromSeq, c.toSeq, c.merkleRoot, c.headHash, c.prevCheckpointHash, c.createdAt].join("\n"))
  .digest("hex");
console.log(node.toString("hex") === p.merkle.root && p.merkle.root === c.merkleRoot && cpHash === c.checkpointHash);
require("fs").writeFileSync("token.der", Buffer.from(p.anchor.tokenBase64, "base64"));
```

**3. The authority's signature.** With OpenSSL and the authority's CA certificates (a system CA bundle includes
DigiCert's):

```bash
openssl ts -verify -digest <checkpoint.checkpointHash> -token_in -in token.der -CAfile /path/to/ca-bundle.crt
openssl ts -reply -token_in -in token.der -token_out -text     # shows the authority's time, policy and hash
```

`Verification: OK` means the authority signed exactly this checkpoint hash at the time shown. The ledger as it stood
then, including the entry, cannot have been rewritten since without contradicting that signature.

## Erased data

When a principal's data is erased, the entry's payload and the link to the principal are deleted; the entry itself, its
hash and every proof stay valid. Verification counts such entries as erased rather than broken.

## Operating it

| Setting | Default | |
|---|---|---|
| `CM_AUD_ANCHORING_ENABLED` | `true` | Turn checkpointing and anchoring off |
| `CM_AUD_ANCHORING_INTERVAL_MS` | `3600000` | How often a checkpoint is made and pending ones are anchored |
| `CM_AUD_TSA_URL` | `http://timestamp.digicert.com` | The RFC 3161 authority |

The service logs `Checkpoint N covers entries a to b` and `Checkpoint N anchored by <authority> at <time>`; a
checkpoint the authority could not take is retried on the next run and logged as `not anchored yet`.
