package com.techeazyhq.cm.cnsqry.app.evt;

/**
 * Structural (untagged) deserialization of cm-cns-cmd-svc's sealed {@code DpRef} — Jackson
 * serializes the concrete record's fields with no type discriminator, so this reads either
 * shape by field presence: {@code dpId} (linked) or {@code typ}/{@code val} (unlinked). The
 * read model only ever needs the linked {@code dpId} (nullable otherwise, plan §7a) — it
 * never needs the raw external contact info.
 */
public record DpRefPayload(String dpId, String typ, String val) {

    public boolean isLinked() {
        return dpId != null;
    }
}
