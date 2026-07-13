# cloud-itonami-isco-4221

Open Business Blueprint for **ISCO-08 4221**: Travel Consultants and Clerks — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — TravelConsultantsAdvisor ⊣
TravelConsultantsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
14 tests / 28 assertions green.

The booking HARD invariants — arithmetic, not a courtesy call:

1. **Inventory arithmetic** — a proposed booking's units must not
   exceed the registered available inventory (you cannot book what
   isn't there).
2. **Refund-cutoff floor** — a proposed cancellation's
   days-before-departure must be ≥ the registered refund-cutoff-days
   to qualify.

Also HARD: unregistered/foreign inventory, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-group-booking` (large-party commitment), low confidence
(< 0.6).



AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
