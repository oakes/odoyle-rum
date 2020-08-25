(ns odoyle.rum
  (:require [odoyle.rules :as o]
            [rum.core :as rum]))

(defmacro compset
  [rules]
  (reduce
    (fn [v {:keys [rule-name fn-name conditions then-body when-body arg]}]
      (conj v `(let [*state# (atom nil)]
                 (rum/defc ~(-> rule-name name symbol)
                   ~'<
                   {:init
                    (fn [state# props#]
                      (let [comp# (:rum/react-component state#)]
                        (add-watch *state# ~rule-name
                                   (fn [_# _# p# n#]
                                     (when (not= p# n#)
                                       (.forceUpdate comp#))))))
                    :will-unmount
                    (fn [state#]
                      (remove-watch *state# ~rule-name))}
                   []
                   (when-let [~arg @*state#]
                     ~@then-body))
                 (o/->Rule ~rule-name
                           (mapv o/map->Condition '~conditions)
                           (fn ~fn-name [arg#] (reset! *state# arg#))
                           ~(when (some? when-body)
                              `(fn [~arg] ~when-body))))))
    []
    (mapv #'o/->rule (#'o/parse ::o/rules rules))))
