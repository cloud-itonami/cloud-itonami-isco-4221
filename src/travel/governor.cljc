(ns travel.governor
  "TravelConsultantsGovernor — the independent safety/traceability
  layer for the ISCO-08 4221 community travel consultants & clerks
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.governor.
  Booking twist: a proposed booking's units are arithmetic comparison
  against the registered available inventory (you cannot book what
  isn't there), and refund eligibility is arithmetic comparison
  against the registered cutoff (refund eligibility is a day-count
  threshold, not a courtesy call).

  Every op-specific check is DERIVED from `travel.operations/catalog`
  rather than restated here. That is the whole point of the catalog:
  when the branches were written out as `(= :approve-booking op)`, an
  op that matched no branch collected no violation and came out
  conforming, and `:approve-group-booking` — the op this actor itself
  names as the dangerous one — was checked against no inventory at all.
  See that namespace's docstring for the three measured escapes.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance  — the organization must be registered.
    2. no-actuation       — proposal :effect must be :propose.
    3. offered op         — the op must be one this actor offers
                            (`travel.operations/offered?`). An op that
                            was never offered is refused, not ignored.
    4. inventory basis    — an inventory-basis op must cite a REGISTERED
                            inventory line belonging to this client.
    5. operand supplied   — every operand the catalog declares must be
                            present and a number of the declared kind.
                            An operand that was never supplied is not an
                            operand that was checked.
    6. registered figure  — the inventory line must carry the registered
                            figure the operand is compared against. A
                            comparison we cannot make is refused, not
                            skipped.
    7. operand arithmetic — each operand must satisfy its declared
                            comparison: booking (and GROUP booking)
                            units <= :available-units, refund
                            days-before-departure >= :refund-cutoff-days.
  ESCALATION invariants (:escalate? true, human sign-off):
    8. ops the catalog marks `:always-escalates?` (currently
       :approve-group-booking — large-party commitment).
    9. low confidence (< `confidence-floor`).

  Escalation is ON TOP OF the hard invariants, never instead of them:
  `travel.actor/approve!` resumes an escalated thread straight to
  `:commit`, so anything that only escalated is something a human was
  asked to sign off with an empty violation list."
  (:require [travel.operations :as ops]
            [travel.store :as store]))

(def confidence-floor 0.6)

(defn- operand-violations
  "One violation per declared operand of `op` that is absent, not a
  number of the declared kind, compared against a figure the inventory
  line does not carry, or failing the declared comparison."
  [op proposal inv]
  (reduce-kv
   (fn [acc field {:keys [kind compare against rule because]}]
     (let [v (get proposal field)
           figure (get inv against)]
       (cond
         (not (ops/operand-ok? kind v))
         (conj acc {:rule :operand-missing
                    :detail (str "operand " field " が " (name kind)
                                 " ではない（" (pr-str v) "）"
                                 "。供給されなかった operand は"
                                 "検査された operand ではない")})

         (not (number? figure))
         (conj acc {:rule :inventory-incomplete
                    :detail (str "inventory " (:inventory-id inv) " に "
                                 against " が登録されていない"
                                 "。比較できない検査を合格にしない")})

         (not (ops/compare-ok? compare v figure))
         (conj acc {:rule rule
                    :detail (str field " " v " が登録済み " against " "
                                 figure " を満たさない（" because "）")})

         :else acc)))
   []
   (ops/operands op)))

(defn- hard-violations [{:keys [request proposal]} client-record inv]
  (let [op (:op proposal)
        offered? (ops/offered? op)
        ;; An unoffered op is refused on rule 3; do not ALSO report it as
        ;; missing an inventory basis it was never defined to have —
        ;; that would name the wrong reason.
        basis? (and offered? (ops/inventory-basis? op))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (not offered?)
      (conj {:rule :unoffered-op
             :detail (str "この actor が提供していない op: " (pr-str op)
                          "。提供しているのは "
                          (pr-str (sort (ops/offered-ops))))})

      (and basis? (nil? inv))
      (conj {:rule :unknown-inventory :detail "未登録 inventory への承認は不可"})

      (and basis? inv (not= (:client-id inv) (:client-id request)))
      (conj {:rule :inventory-wrong-client :detail "inventory が別 client のもの"})

      ;; Operand arithmetic runs for EVERY offered op that declares
      ;; operands, group bookings included.
      (and offered? inv)
      (into (operand-violations op proposal inv)))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `travel.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        inv (some->> (:inventory-id proposal) (store/inventory store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record inv)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (ops/always-escalates? (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
