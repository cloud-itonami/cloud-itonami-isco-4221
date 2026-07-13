(ns travel.store
  "SSoT for the ISCO-08 4221 community travel consultants & clerks
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client    — a registered organization (:client-id, :name)
    inventory — a registered bookable inventory line {:inventory-id
                :client-id :name :available-units number
                :refund-cutoff-days number}. `:available-units` is
                the registered current stock a proposed booking's
                units must not exceed; `:refund-cutoff-days` is the
                registered minimum days-before-departure a proposed
                cancellation must meet to qualify for refund (refund
                eligibility is a day-count threshold, not a courtesy
                call).
    record    — a committed operating record (approved booking or
                refund) — written ONLY via commit-record!.
    ledger    — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (inventory [s inventory-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-inventory! [s inv])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (inventory [_ inventory-id] (get-in @a [:inventories inventory-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-inventory! [s inv]
    (swap! a assoc-in [:inventories (:inventory-id inv)] inv) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :inventories {} :records [] :ledger []}
                                   seed)))))
