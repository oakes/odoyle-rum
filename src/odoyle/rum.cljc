(ns odoyle.rum
  (:require [odoyle.rules :as o]
            [rum.core :as rum]))

(defmacro compset
  [rules]
  (reduce
    (fn [v {:keys [rule-name fn-name conditions then-body when-body arg]}]
      (conj v `(let [*state# (atom nil)]
                 (rum/defc ~(-> rule-name name symbol) ~'< rum/reactive []
                   (when-let [~arg (rum/react *state#)]
                     ~@then-body))
                 (o/->Rule ~rule-name
                           (mapv o/map->Condition '~conditions)
                           (fn ~fn-name [arg#] (reset! *state# arg#))
                           ~(when (some? when-body)
                              `(fn [~arg] ~when-body))))))
    []
    (mapv #'o/->rule (#'o/parse ::o/rules rules))))
