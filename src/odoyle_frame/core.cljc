(ns odoyle-frame.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]))

(defn click [state]
  (-> state
      (o/insert ::global ::event ::click)
      o/fire-rules))

(rum/defc app < rum/reactive [*state]
  (let [{:keys [clicks]} (-> (rum/react *state)
                             (o/query-all ::get-clicks)
                             first)]
    [:button {:on-click #(swap! *state click)}
     (str "Clicked " clicks " " (if (= 1 clicks) "time" "times"))]))

(def rules
  (o/ruleset
    {::on-click
     [:what
      [::global ::event ::click]
      [::global ::clicks clicks {:then false}]
      :then
      (o/insert! ::global ::clicks (inc clicks))]
     ::get-clicks
     [:what
      [::global ::clicks clicks]]}))

(def *state (-> (reduce o/add-rule (o/->session) rules)
                (o/insert ::global ::clicks 0)
                atom))
