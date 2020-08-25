(ns odoyle.rum
  (:require [odoyle.rules :as o]
            [rum.core :as rum])
  (:refer-clojure :exclude [atom]))

(def ^:dynamic *local-pointer* nil)
(def ^:dynamic *react-component* nil)
(def ^:dynamic *can-return-atom?* nil)

#?(:clj (defn rand-uuid []
          (.toString (java.util.UUID/randomUUID)))
   :cljs (def rand-uuid random-uuid))

(defn atom [initial-value]
  (cond
    (nil? *local-pointer*)
    (throw (ex-info "You cannot create an atom here" {}))
    (nil? @*can-return-atom?*)
    (throw (ex-info "You can only call `atom` once in each :then block" {})))
  (vreset! *can-return-atom?* false)
  (if-let [*local @*local-pointer*]
    *local
    (let [*local (reset! *local-pointer* (clojure.core/atom initial-value))]
      (when-let [cmp *react-component*]
        (add-watch *local ::local
                   (fn [_ _ p n]
                     (when (not= p n)
                       (.forceUpdate cmp)))))
      *local)))

(defmacro compset
  [rules]
  (reduce
    (fn [v {:keys [rule-name fn-name conditions then-body when-body arg]}]
      (conj v `(let [*state# (clojure.core/atom nil)
                     global-key# (rand-uuid)]
                 (rum/defc ~(-> rule-name name symbol)
                   ~'<
                   {:init
                    (fn [state# props#]
                      (when-let [comp# (:rum/react-component state#)]
                        (add-watch *state# global-key#
                                   (fn [_# _# p# n#]
                                     (when (not= p# n#)
                                       (.forceUpdate comp#)))))
                      (assoc state# ::local-pointer (clojure.core/atom nil)))
                    :wrap-render
                    (fn [render-fn#]
                      (fn [state#]
                        (binding [*local-pointer* (::local-pointer state#)
                                  *react-component* (:rum/react-component state#)
                                  *can-return-atom?* (volatile! true)]
                          (render-fn# state#))))
                    :will-unmount
                    (fn [state#]
                      (remove-watch *state# global-key#)
                      (when-let [*local# @(::local-pointer state#)]
                        (remove-watch *local# ::local))
                      (dissoc state# ::local-pointer))}
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
