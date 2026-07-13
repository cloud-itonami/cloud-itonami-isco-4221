(ns travel.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [travel.store :as store]
            [travel.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-inventory! st {:inventory-id "INV-1" :client-id "client-1"
                                   :name "coach-tour-101"
                                   :available-units 20
                                   :refund-cutoff-days 14})
    st))

(defn- book [units]
  {:op :approve-booking :effect :propose :inventory-id "INV-1"
   :units units :confidence 0.9 :stake :low})

(defn- refund [days]
  {:op :approve-refund :effect :propose :inventory-id "INV-1"
   :days-before-departure days :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-booking-within-inventory
  (let [st (fresh-store)
        v (governor/check req {} (book 5) st)]
    (is (:ok? v))))

(deftest ok-booking-at-exact-inventory
  (testing "booking exactly the available units is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (book 20) st)]
      (is (:ok? v)))))

(deftest hard-on-insufficient-inventory
  (testing "you cannot book what isn't there"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (book 30) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :insufficient-inventory (:rule %)) (:violations v))))))

(deftest ok-refund-at-exact-cutoff
  (testing "refund exactly at the cutoff day qualifies"
    (let [st (fresh-store)
          v (governor/check req {} (refund 14) st)]
      (is (:ok? v)))))

(deftest hard-on-refund-cutoff-not-met
  (testing "refund eligibility is arithmetic, not a courtesy call"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (refund 3) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :refund-cutoff-not-met (:rule %)) (:violations v))))))

(deftest hard-on-unknown-inventory
  (let [st (fresh-store)
        v (governor/check req {} (assoc (book 5) :inventory-id "INV-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-inventory (:rule %)) (:violations v)))))

(deftest hard-on-foreign-inventory
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (book 5) st)]
      (is (:hard? v))
      (is (some #(= :inventory-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (book 5) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (book 5) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-group-booking
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-group-booking :effect :propose
                                  :inventory-id "INV-1" :units 5 :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (book 5) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
