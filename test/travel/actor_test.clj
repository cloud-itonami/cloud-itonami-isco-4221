(ns travel.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [travel.actor :as actor]
            [travel.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-inventory! st {:inventory-id "INV-1" :client-id "client-1"
                                   :name "coach-tour-101"
                                   :available-units 20
                                   :refund-cutoff-days 14})
    st))

(deftest commits-an-in-inventory-booking
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-booking :stake :low
                 :inventory-id "INV-1" :units 5}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-inventory-booking
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-booking :stake :low
                 :inventory-id "INV-1" :units 50}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-group-booking-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-group-booking :stake :high
                 :inventory-id "INV-1" :units 5}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))

(deftest holds-an-over-inventory-group-booking-instead-of-asking-a-human
  (testing "approve! resumes an escalation straight to :commit, so an escalation
  carrying no violations is a human signing off blind. 500 units against 20 must
  reach :hold, not :request-approval."
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:client-id "client-1" :op :approve-group-booking :stake :high
                   :inventory-id "INV-1" :units 500}
          result (actor/run-request! graph request {} "thread-4")]
      (is (not= :interrupted (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "client-1")))
      (is (some #(= :insufficient-inventory (:rule %))
                (get-in result [:state :verdict :violations]))))))

(deftest holds-an-unoffered-op
  (testing "the mock advisor copies the request's :op through verbatim"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:client-id "client-1" :op :wire-deposit-to-me :stake :low
                   :inventory-id "INV-1" :units 1}
          result (actor/run-request! graph request {} "thread-5")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "client-1"))))))

(deftest holds-a-booking-that-supplied-no-units
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-booking :stake :low
                 :inventory-id "INV-1"}
        result (actor/run-request! graph request {} "thread-6")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))
