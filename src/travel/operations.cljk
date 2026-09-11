(ns travel.operations
  "The operations this ISCO-08 4221 travel actor OFFERS, as data.

  This namespace exists because the governor used to ask
  `(= :approve-booking op)` and `(= :approve-refund op)` directly, which
  gave the actor no declared offer surface. Three consequences, all
  measured on this repo before this catalog existed:

    1. An op nobody offered — `:wire-deposit-to-me`, or the
       `{:op :unknown}` that `travel.advisor/parse-proposal` itself
       emits on an unparseable LLM response — matched neither branch,
       collected no violation, and came out `:ok? true`. No adversary
       was needed: `travel.advisor/infer` copies the request's `:op`
       through verbatim, and the default `:low` stake mints confidence
       0.95, above the escalation floor.
    2. `:approve-group-booking` — the one op the actor itself names as
       the dangerous one — was not `:approve-booking`, so it was checked
       against no inventory at all. 500 units against 20 available, a
       ghost inventory line, another client's inventory: all produced
       `:violations []`, escalated to a human, and `travel.actor/approve!`
       resumes an escalation straight to `:commit`. The human signing it
       off was shown an empty violation list.
    3. The arithmetic was guarded by `(number? units)`, so a booking that
       simply omitted `:units` skipped the check and passed. An operand
       that was never supplied is not an operand that was checked.

  So the catalog is the answer to `is this op offered, and what must be
  supplied and compared before it can be conforming?` — and the governor
  derives every one of those checks from here rather than restating them.
  Adding an operation here is what makes it reachable; it cannot be
  reached by not being mentioned.

  An operand entry reads: `:units` must be a `:positive-number`, and it
  must be `:at-most` the inventory's registered `:available-units`;
  failing that comparison is a `:insufficient-inventory` violation."
  (:refer-clojure :exclude [compare]))

(def catalog
  "op -> {:label :inventory-basis? :always-escalates? :operands}."
  {:approve-booking
   {:label "予約承認"
    :inventory-basis? true
    :always-escalates? false
    :operands {:units {:kind :positive-number
                       :compare :at-most
                       :against :available-units
                       :rule :insufficient-inventory
                       :because "存在しない在庫は予約できない"}}}

   :approve-refund
   {:label "返金承認"
    :inventory-basis? true
    :always-escalates? false
    :operands {:days-before-departure {:kind :non-negative-number
                                       :compare :at-least
                                       :against :refund-cutoff-days
                                       :rule :refund-cutoff-not-met
                                       :because "返金資格は日数の算術であって温情の電話ではない"}}}

   :approve-group-booking
   {:label "団体予約承認"
    :inventory-basis? true
    ;; Large-party commitment: always human sign-off. The escalation is
    ;; ON TOP OF the inventory arithmetic below, not instead of it — a
    ;; human cannot sign off on a violation they were never shown.
    :always-escalates? true
    :operands {:units {:kind :positive-number
                       :compare :at-most
                       :against :available-units
                       :rule :insufficient-inventory
                       :because "存在しない在庫は団体でも予約できない"}}}})

(defn offered?
  "Is `op` an operation this actor offers at all?"
  [op]
  (contains? catalog op))

(defn offered-ops [] (set (keys catalog)))

(defn spec [op] (get catalog op))

(defn inventory-basis?
  "Must a proposal for `op` cite a registered inventory line? An
  unoffered op is false here — it is refused by `offered?` first, and
  answering `true` would report the wrong reason."
  [op]
  (boolean (:inventory-basis? (spec op))))

(defn always-escalates?
  "Does `op` require human sign-off regardless of the verdict?"
  [op]
  (boolean (:always-escalates? (spec op))))

(defn operands
  "field -> operand spec for `op`; empty for an unoffered op."
  [op]
  (or (:operands (spec op)) {}))

(defn operand-ok?
  "Is `v` a supplied value of the declared `kind`? `nil`, a non-number
  and an unknown kind are all false — the point of this predicate is
  that an absent operand must not read the same as a checked one."
  [kind v]
  (case kind
    :positive-number     (and (number? v) (pos? v))
    :non-negative-number (and (number? v) (not (neg? v)))
    false))

(defn compare-ok?
  "Does `v` satisfy `cmp` against the registered `figure`? An unknown
  comparison is false, so a typo in the catalog refuses rather than
  passes."
  [cmp v figure]
  (case cmp
    :at-most  (<= v figure)
    :at-least (>= v figure)
    false))
