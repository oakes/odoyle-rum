(ns odoyle.rum
  (:require [odoyle.rules :as o]
            [rum.core :as rum]
            [clojure.spec.alpha :as s])
  (:refer-clojure :exclude [atom]))

(def ^:private ^:dynamic *local-pointer* nil)
(def ^:private ^:dynamic *react-component* nil)
(def ^:private ^:dynamic *can-return-atom?* nil)
(def ^:private ^:dynamic *prop* nil)

#?(:clj (defn- random-uuid []
          (.toString (java.util.UUID/randomUUID))))

(defn atom
  "Returns an atom that can hold local state for a component.
  Only works in a :then block."
  [initial-value]
  (when-not *local-pointer*
    (throw (ex-info "You cannot call `atom` here" {})))
  (when-not @*can-return-atom?*
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

(defn prop
  "Returns the prop sent to the component. Only works in a :then block."
  []
  (when-not *local-pointer*
    (throw (ex-info "You cannot call `prop` here" {})))
  *prop*)

(defn reactive
  "A rum mixin that makes the associated component react to changes from
  the session and the local atom."
  [*state]
  {:init
   (fn [state props]
     (let [global-key (random-uuid)]
       (when-let [cmp (:rum/react-component state)]
         (add-watch *state global-key
                    (fn [_ _ p n]
                      (when (not= p n)
                        (.forceUpdate cmp)))))
       (assoc state
         ::local-pointer (clojure.core/atom nil)
         ::global-key global-key)))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (binding [*local-pointer* (::local-pointer state)
                 *react-component* (:rum/react-component state)
                 *can-return-atom?* (volatile! true)
                 *prop* (first (:rum/args state))]
         (render-fn state))))
   :will-unmount
   (fn [state]
     (remove-watch *state (::global-key state))
     (when-let [*local @(::local-pointer state)]
       (remove-watch *local ::local))
     (dissoc state ::local-pointer))})

(s/def ::rules (s/map-of simple-symbol? ::o/rule))

(defmacro ruleset
  "Returns a vector of component rules after transforming the given map."
  [rules]
  (->> (o/parse ::rules rules)
       (mapv o/->rule)
       (reduce
         (fn [v {:keys [rule-name conditions then-body when-body arg]}]
           (let [;; the rule name is a simple symbol,
                 ;; so create a qualified keyword to use as the rule name.
                 ;; this is necessary so rules with the same name can be
                 ;; created in different namespaces.
                 rule-key (keyword (str *ns*) (name rule-name))]
             (conj v `(let [*state# (clojure.core/atom nil)]
                        (rum/defc ~(-> rule-name name symbol) ~'< (reactive *state#) [prop#]
                          (when-let [state# @*state#]
                            (binding [o/*match* state#]
                              (let [~arg state#]
                                ~@then-body))))
                        (o/->Rule ~rule-key
                                  (mapv o/map->Condition '~conditions)
                                  (fn [arg#] (reset! *state# arg#))
                                  ~(when (some? when-body)
                                     `(fn [~arg] ~when-body)))))))
         [])
       (list 'do
         `(declare ~@(map #(-> % name symbol) (keys rules))))))
