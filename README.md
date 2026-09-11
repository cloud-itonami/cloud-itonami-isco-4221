# cloud-itonami-isco-4221

Open Business Blueprint for **ISCO-08 4221**: Travel Consultants and Clerks — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — TravelConsultantsAdvisor ⊣
TravelConsultantsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
36 tests / 83 assertions green.

**What this actor offers is declared as data** in `travel.operations`,
and the governor derives every op-specific check from that catalog.
An op is reachable by being named there; it cannot be reached by not
being mentioned.

The booking HARD invariants — arithmetic, not a courtesy call:

1. **Offered op** — the op must be one the catalog names. An op that
   was never offered is refused, not ignored.
2. **Inventory arithmetic** — a proposed booking's units must not
   exceed the registered available inventory (you cannot book what
   isn't there). This applies to **group** bookings too, which
   escalate *on top of* being checked, never instead of it.
3. **Refund-cutoff floor** — a proposed cancellation's
   days-before-departure must be ≥ the registered refund-cutoff-days
   to qualify.
4. **Operand supplied** — every operand the catalog declares must be
   present and a number of the declared kind. An operand that was
   never supplied is not an operand that was checked.
5. **Registered figure** — the inventory line must carry the figure the
   operand is compared against. A comparison we cannot make is refused,
   not skipped.

Also HARD: unregistered/foreign inventory, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-group-booking` (large-party commitment), low confidence
(< 0.6).

Invariants 1, 2-for-group-bookings, 4 and 5 are regressions: each was
measured escaping this governor before the catalog existed, and each
has a test that goes red when the rule is removed. The mutations live
in the superproject's `scripts/maturity-loop/mutations.edn`
(`kbb --backend sci scripts/maturity-loop/run.cljk --only cloud-itonami-isco-4221`).



AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
