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

package com.techeazyhq.cm.cnscmd.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How a purpose version differs from the one before (ADR-031 §2), decided by the platform, never the fiduciary. The
 * comparison is field by field, so the same two versions always classify the same way.
 *
 * @param material   reasons the change needs re-consent: the principal would be agreeing to more than they did
 * @param minor      the other differences: principals are told, but their consent still holds
 */
public record PrpsChange(Kind kind, List<String> material, List<String> minor) {

    public enum Kind {
        /** The purpose's first version: nothing to compare with. */
        INITIAL,
        /** Existing consents keep the old scope; the fiduciary asks again for the new one. */
        MATERIAL,
        /** Existing consents carry on; principals are told what changed. */
        NON_MATERIAL
    }

    public PrpsChange {
        material = List.copyOf(material);
        minor = List.copyOf(minor);
    }

    public static PrpsChange initial() {
        return new PrpsChange(Kind.INITIAL, List.of(), List.of());
    }

    public boolean isNone() {
        return kind != Kind.INITIAL && material.isEmpty() && minor.isEmpty();
    }

    /** Every difference, material first. */
    public List<String> reasons() {
        List<String> all = new ArrayList<>(material);
        all.addAll(minor);
        return all;
    }

    public static PrpsChange between(PrpsVerContent before, PrpsVerContent after) {
        List<String> material = new ArrayList<>();
        List<String> minor = new ArrayList<>();

        for (PrpsDCat d : after.dCats()) {
            before.dCat(d.code()).ifPresentOrElse(old -> {
                if (d.sensitivity().isAbove(old.sensitivity())) {
                    material.add(d.code() + " is now " + d.sensitivity() + ", was " + old.sensitivity());
                } else if (old.sensitivity().isAbove(d.sensitivity())) {
                    minor.add(d.code() + " is now " + d.sensitivity() + ", was " + old.sensitivity());
                }
                if (d.necessary() != old.necessary()) {
                    minor.add(d.code() + (d.necessary() ? " is now marked necessary" : " is no longer marked necessary"));
                } else if (!Objects.equals(d.consequence(), old.consequence())) {
                    minor.add("What happens without " + d.code() + " is worded differently");
                }
                if (!Objects.equals(d.dpv(), old.dpv())) {
                    minor.add(d.code() + " has a different DPV type");
                }
            }, () -> material.add("Asks for more data: " + d.code()));
        }
        before.dCats().stream().filter(d -> after.dCat(d.code()).isEmpty())
                .forEach(d -> minor.add("No longer asks for " + d.code()));

        compareParties(before.recipients(), after.recipients(), "recipient", material, minor);
        compareParties(before.transfers(), after.transfers(), "transfer", material, minor);

        if (after.retentionDays() > before.retentionDays()) {
            material.add("Keeps data longer: " + after.retentionDays() + " days, was " + before.retentionDays());
        } else if (after.retentionDays() < before.retentionDays()) {
            minor.add("Keeps data for less time: " + after.retentionDays() + " days, was " + before.retentionDays());
        }
        if (after.maxValidityDays() > before.maxValidityDays()) {
            material.add("Consent may last longer: up to " + after.maxValidityDays() + " days, was "
                    + before.maxValidityDays());
        } else if (after.maxValidityDays() < before.maxValidityDays()) {
            minor.add("Consent may last up to " + after.maxValidityDays() + " days, was " + before.maxValidityDays());
        }
        if (after.requestedValidityDays() != before.requestedValidityDays()) {
            minor.add("Asks for consent for " + after.requestedValidityDays() + " days, was "
                    + before.requestedValidityDays());
        }

        after.flags().stream().filter(f -> !before.flags().contains(f)).sorted()
                .forEach(f -> material.add("New kind of processing: " + f));
        before.flags().stream().filter(f -> !after.flags().contains(f)).sorted()
                .forEach(f -> minor.add("No longer " + f));

        if (!Objects.equals(before.name(), after.name())) {
            minor.add("Renamed");
        }
        if (!Objects.equals(blankToNull(before.description()), blankToNull(after.description()))) {
            minor.add("The description is worded differently");
        }
        for (PrpsNtc n : after.notices()) {
            before.notices().stream().filter(b -> b.lang().equals(n.lang())).findFirst()
                    .ifPresentOrElse(old -> {
                        if (!old.hash().equals(n.hash())) {
                            minor.add("The " + n.lang() + " notice is worded differently");
                        }
                    }, () -> minor.add("A notice in " + n.lang()));
        }
        before.notices().stream().filter(n -> after.notices().stream().noneMatch(a -> a.lang().equals(n.lang())))
                .forEach(n -> minor.add("No notice in " + n.lang() + " any more"));

        return new PrpsChange(material.isEmpty() ? Kind.NON_MATERIAL : Kind.MATERIAL, material, minor);
    }

    private static void compareParties(List<String> before, List<String> after, String what, List<String> material,
                                       List<String> minor) {
        after.stream().filter(p -> !before.contains(p)).forEach(p -> material.add("New " + what + ": " + p));
        before.stream().filter(p -> !after.contains(p)).forEach(p -> minor.add("No longer a " + what + ": " + p));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
