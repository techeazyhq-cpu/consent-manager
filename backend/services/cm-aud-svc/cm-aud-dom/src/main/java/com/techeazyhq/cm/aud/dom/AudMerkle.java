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

package com.techeazyhq.cm.aud.dom;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Merkle tree hashing as in RFC 6962 (Certificate Transparency), over ledger entry hashes (ADR-015). A leaf is
 * SHA-256(0x00 || entryHash), an inner node SHA-256(0x01 || left || right), and a tree of n leaves splits at the largest
 * power of two below n. The domain-separation bytes stop a leaf from being passed off as an inner node.
 *
 * <p>An inclusion proof lists the sibling hashes from the leaf up to the root, each with the side it sits on, so anyone
 * holding one entry and its proof can recompute the root a checkpoint committed to.
 */
public final class AudMerkle {

    /** One step up the tree: the sibling's hash and whether it is to the left of the path. */
    public record Step(boolean siblingOnLeft, String hash) {
    }

    private static final HexFormat HEX = HexFormat.of();

    private AudMerkle() {
    }

    public static String root(List<String> entryHashes) {
        if (entryHashes.isEmpty()) {
            throw new IllegalArgumentException("A Merkle tree needs at least one leaf");
        }
        return HEX.formatHex(subtree(entryHashes, 0, entryHashes.size()));
    }

    /** The path from leaf {@code index} to the root, bottom first. */
    public static List<Step> proof(List<String> entryHashes, int index) {
        if (index < 0 || index >= entryHashes.size()) {
            throw new IllegalArgumentException("No leaf " + index + " in a tree of " + entryHashes.size());
        }
        List<Step> steps = new ArrayList<>();
        path(entryHashes, 0, entryHashes.size(), index, steps);
        return steps;
    }

    /** Whether {@code entryHash}, walked up {@code proof}, arrives at {@code expectedRoot}. */
    public static boolean verify(String entryHash, List<Step> proof, String expectedRoot) {
        byte[] node = leaf(entryHash);
        for (Step step : proof) {
            byte[] sibling = HEX.parseHex(step.hash());
            node = step.siblingOnLeft() ? inner(sibling, node) : inner(node, sibling);
        }
        return HEX.formatHex(node).equals(expectedRoot);
    }

    private static byte[] subtree(List<String> leaves, int from, int to) {
        int n = to - from;
        if (n == 1) {
            return leaf(leaves.get(from));
        }
        int k = split(n);
        return inner(subtree(leaves, from, from + k), subtree(leaves, from + k, to));
    }

    private static void path(List<String> leaves, int from, int to, int index, List<Step> steps) {
        int n = to - from;
        if (n == 1) {
            return;
        }
        int k = split(n);
        if (index < from + k) {
            path(leaves, from, from + k, index, steps);
            steps.add(new Step(false, HEX.formatHex(subtree(leaves, from + k, to))));
        } else {
            path(leaves, from + k, to, index, steps);
            steps.add(new Step(true, HEX.formatHex(subtree(leaves, from, from + k))));
        }
    }

    private static int split(int n) {
        int k = Integer.highestOneBit(n);
        return k == n ? k / 2 : k;
    }

    private static byte[] leaf(String entryHash) {
        return sha256(new byte[] {0x00}, HEX.parseHex(entryHash));
    }

    private static byte[] inner(byte[] left, byte[] right) {
        return sha256(new byte[] {0x01}, left, right);
    }

    private static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
