(ns travel.probe-test
  (:require [clojure.test :refer [deftest is testing]]
            [travel.store :as store]
            [travel.governor :as governor]
            [travel.advisor :as advisor]
            [travel.actor :as actor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-inventory! st {:inventory-id "INV-1" :client-id "client-1"
                                   :name "coach-tour-101"
                                   :available-units 20
                                   :refund-cutoff-days 14})
    st))
(def ^:private req {:client-id "client-1"})

;; DEFECT 1 — an op this actor never offered sails through to :ok? true.
(deftest probe-unoffered-op
  (let [st (fresh-store)
        v (governor/check req {} {:op :wire-deposit-to-me :effect :propose
                                  :confidence 0.95 :stake :low} st)]
    (is (not (:ok? v)) "an unoffered op must not be conforming")))

;; DEFECT 1b — reachable with no adversary: the advisor passes request :op through.
(deftest probe-unoffered-op-reachable-through-advisor
  (let [st (fresh-store)
        p (advisor/-advise (advisor/mock-advisor) st
                           {:client-id "client-1" :op :wire-deposit-to-me})
        v (governor/check req {} p st)]
    (is (not (:ok? v)) "the mock advisor itself can mint an unoffered op")))

;; DEFECT 2 — the *named dangerous* op skips every inventory check.
(deftest probe-group-booking-over-inventory
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-group-booking :effect :propose
                                  :inventory-id "INV-1" :units 500
                                  :confidence 0.9 :stake :high} st)]
    (is (seq (:violations v)) "500 units against 20 must show a violation")))

(deftest probe-group-booking-no-inventory-cited
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-group-booking :effect :propose
                                  :units 5 :confidence 0.9 :stake :high} st)]
    (is (seq (:violations v)) "a group booking citing no inventory must violate")))

(deftest probe-group-booking-foreign-inventory
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {}
                            {:op :approve-group-booking :effect :propose
                             :inventory-id "INV-1" :units 5
                             :confidence 0.9 :stake :high} st)]
      (is (seq (:violations v)) "a group booking on another client's inventory must violate"))))

;; DEFECT 2c — end to end: the human is asked to approve, shown nothing, and it commits.
(deftest probe-human-approves-overbooking-blind
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-group-booking :stake :high
                 :inventory-id "INV-1" :units 500}
        interrupted (actor/run-request! graph request {} "probe-thread")]
    (is (not= :interrupted (:status interrupted))
        "over-inventory group booking must not reach human approval as a clean escalation")))

;; DEFECT 3 — a missing number skips the arithmetic entirely.
(deftest probe-booking-without-units
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-booking :effect :propose
                                  :inventory-id "INV-1" :confidence 0.9 :stake :low} st)]
    (is (not (:ok? v)) "a booking with no :units must not be conforming")))

(deftest probe-refund-without-days
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-refund :effect :propose
                                  :inventory-id "INV-1" :confidence 0.9 :stake :low} st)]
    (is (not (:ok? v)) "a refund with no :days-before-departure must not be conforming")))
