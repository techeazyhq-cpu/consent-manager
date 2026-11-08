# ADR-081: What a version says about the parties it names

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #89
- **Completes:** the first "Not yet" item of [ADR-075](adr-075-processor-registry.md)

## Context

ADR-075 gave a fiduciary a register of its processors (what each does with the data, where, how to reach it) and stopped retired
ones being named, but the person deciding on a consent still saw only the names the fiduciary typed into a purpose's recipients and
transfers. The register's details never reached them, so for the person it changed nothing. A notice has to be true on the day
it is shown and on the day it is looked at later (ADR-031), so the details cannot simply be read from the register at display time:
the register is edited, and a published version must not change under the people who agreed to it.

## Decision

1. **A version carries a copy of the details of the parties it names** (`parties`: the name as the version spells it, the role
   and the country), taken from the register **when the version is published** and kept with the version for good. A published
   version is already immutable (ADR-031), so the copy is too: editing or retiring a processor afterwards does not touch it.
2. **Only registered names get details.** A recipient or transfer that is not in the register (the register is opt-in:
   ADR-075) has none, and the version still reads as it always did. Names are matched ignoring case and spacing, once each, in the
   order the version names them, spelled as the version spells them.
3. **The fiduciary does not write them.** A draft shows no details (`parties` is read only and ignored on the way in); publishing
   fills them in from the register as it is at that moment, and refuses a version that names a retired processor (ADR-075).
4. **The change classification and the notice hash are untouched.** Details are not compared when a version is classified as
   material or not, and the notice hash (title and text) stays the hash of the notice. A version is published only if something
   else changed, so an edit to the register alone never creates a version.
5. **The person sees them.** The decision screen lists each party with what it does and where ("Courier Ltd: Delivers your
   orders (India)", the country by name in the person's language), after the recipients; the fiduciary sees them on the purpose.
6. **Versions published before this have none** and read exactly as before; nothing is migrated.

## Not yet / limits

- **To change what a version says about a party, publish a new version.** Editing the register changes the next version, never the
  last. A fiduciary that corrects a role must republish for people to see it, which means a change to the purpose; whether such a
  correction needs re-consent is not decided here (it is classified without the details, so it may not).
- **The record of consent does not carry them yet:** the ISO/IEC TS 27560 record and the receipt name the notice by its hash, and
  the details are not part of that hash, so a record proves which notice text was shown but not which details were beside it.
  Putting them in the record needs a decision about the hash and is the next step.
- **Only the decision screen and the purpose page show them.** The consent history and the mobile app show recipients as names.
- **A contact address is not copied:** the person is shown who and what, not how to reach the processor; a way to reach a
  processor is the fiduciary's grievance contact (ADR-058).
- **Role and country are free text and an ISO code the fiduciary typed,** shown as written; the platform does not check them.

## Options considered

- **Reading the register at display time:** a published version would change under people who agreed to it.
- **Putting the details into the notice hash:** the strongest proof, but it changes the hash of every notice that names a processor
  whenever the register's wording changes, and would need re-hashing records. Left for the decision above.
- **Making the fiduciary retype the details in each purpose:** the drift ADR-075 exists to remove.
