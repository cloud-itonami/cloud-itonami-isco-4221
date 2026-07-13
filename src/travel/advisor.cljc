(ns travel.advisor
  "TravelConsultantsAdvisor — proposes a booking operation (approve a
  booking, approve a refund, approve a group booking) for a
  registered organization. Swappable mock/llm; the advisor ONLY
  proposes — `travel.governor` checks inventory arithmetic and the
  refund-cutoff floor independently. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-booking|:approve-refund|:approve-group-booking
               :effect :propose :inventory-id str :units number
               :days-before-departure number :stake kw :confidence n
               :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake inventory-id units days-before-departure] :as request}]
  {:op op
   :effect :propose
   :inventory-id inventory-id
   :units units
   :days-before-departure days-before-departure
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a travel consultant advisor. Given a request, propose an
   :op, the :inventory-id, :units and/or :days-before-departure, an
   honest :confidence and a :stake. Never call an over-inventory
   booking or a too-late refund conforming — the governor checks both
   against the registered inventory record.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
