(ns odoyle-frame.core
  (:require [rum.core :as rum]
            [odoyle.rules :as o]
            [odoyle.rum :as r]))

(defn click [state]
  (-> state
      (o/insert ::global ::event ::click)
      o/fire-rules))

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

(declare *state)

(def comps
  (r/compset
    {::app
     [:what
      [::global ::clicks clicks]
      :then
      [:button {:on-click #(swap! *state click)}
       (str "Clicked " clicks " " (if (= 1 clicks) "time" "times"))]]}))

(def *state (-> (reduce o/add-rule (o/->session) (concat rules comps))
                (o/insert ::global ::clicks 0)
                o/fire-rules
                atom))

