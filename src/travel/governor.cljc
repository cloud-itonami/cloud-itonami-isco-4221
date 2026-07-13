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

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. inventory basis      — an approval must cite a REGISTERED
                           inventory line belonging to this client.
    4. inventory arithmetic — a proposed booking's units must not
                           exceed the inventory's registered
                           :available-units (you cannot book what
                           isn't there).
    5. refund-cutoff floor  — a proposed cancellation's
                           days-before-departure must be >= the
                           inventory's registered :refund-cutoff-days
                           to qualify (arithmetic, not a courtesy
                           call).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-group-booking (large-party commitment).
    7. low confidence (< `confidence-floor`)."
  (:require [travel.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record inv]
  (let [{:keys [op units days-before-departure]} proposal
        book? (= :approve-booking op)
        refund? (= :approve-refund op)
        inv-op? (or book? refund?)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and inv-op? (nil? inv))
      (conj {:rule :unknown-inventory :detail "未登録 inventory への承認は不可"})

      (and inv-op? inv (not= (:client-id inv) (:client-id request)))
      (conj {:rule :inventory-wrong-client :detail "inventory が別 client のもの"})

      (and book? inv (number? units) (> units (:available-units inv)))
      (conj {:rule :insufficient-inventory
             :detail (str "予約数量 " units " > 在庫 " (:available-units inv)
                          "（存在しない在庫は予約できない）")})

      (and refund? inv (number? days-before-departure)
           (< days-before-departure (:refund-cutoff-days inv)))
      (conj {:rule :refund-cutoff-not-met
             :detail (str "出発前日数 " days-before-departure " < 登録済み下限 "
                          (:refund-cutoff-days inv)
                          "（返金資格は日数の算術であって温情の電話ではない）")}))))

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
        risky-op? (= :approve-group-booking (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
