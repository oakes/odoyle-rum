(ns odoyle.rum
  (:require [odoyle.rules :as o]
            [rum.core :as rum]
            [clojure.spec.alpha :as s])
  #?(:cljs (:require-macros [odoyle.rum :refer [ruleset]]))
  (:refer-clojure :exclude [atom]))

(def ^:private ^:dynamic *local-pointer* nil)
(def ^:private ^:dynamic *react-component* nil)
(def ^:private ^:dynamic *can-return-atom?* nil)
(def ^:private ^:dynamic *prop* nil)
(def ^{:dynamic true
       :doc "If bound to a volatile containing a hash map,
            matches triggered by `fire-rules` will be stored in it
            rather than in the atom created by `ruleset`.
            This is important when modifying the session in a server-side
            route because it ensures the modifications will be local
            and will not affect other connections happening simultaneously.
            Do not use it from clojurescript."}
  *matches* nil)

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
  [rule-key *match]
  {:init
   (fn [state props]
     (-> state
         (assoc ::local-pointer (clojure.core/atom nil))
         #?(:cljs (assoc ::global-key
                         (let [global-key (random-uuid)
                               cmp (:rum/react-component state)]
                           (add-watch *match global-key
                                      (fn [_ _ p n]
                                        (when (not= p n)
                                          (.forceUpdate cmp))))
                           global-key)))))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (binding [*local-pointer* (::local-pointer state)
                 *react-component* (:rum/react-component state)
                 *can-return-atom?* (volatile! true)
                 *prop* (first (:rum/args state))
                 o/*match* #?(:cljs @*match
                              :clj (or (some-> *matches* deref rule-key)
                                       @*match))]
         (render-fn state))))
   :will-unmount
   (fn [state]
     #?(:cljs (remove-watch *match (::global-key state)))
     (when-let [*local @(::local-pointer state)]
       (remove-watch *local ::local))
     (dissoc state ::local-pointer))})

;; the specs for the ruleset macro are mostly the same as odoyle.rules/ruleset, except:
;; 1. the keys are symbols, not keywords
;; 2. :what blocks are optional
;; 3. in the :what block, only values can have bindings
;; 4. :when blocks aren't allowed
;; 5. :then blocks are required
(s/def ::what-id (s/or :value (s/and ::o/id #(not (symbol? %)))))
(s/def ::what-value (s/or :binding symbol?))
(s/def ::what-tuple (s/cat :id ::what-id, :attr ::o/what-attr, :value ::what-value, :opts (s/? ::o/what-opts)))
(s/def ::what-block (s/cat :header #{:what} :body (s/+ (s/spec ::what-tuple))))
(s/def ::rule (s/cat
                :what-block (s/? ::what-block)
                :then-block ::o/then-block))
(s/def ::rules (s/map-of simple-symbol? ::rule))

#?(:clj
(defmacro ruleset
  "Returns a vector of component rules after transforming the given map."
  [rules]
  (->> (o/parse ::rules rules)
       (mapv o/->rule)
       (reduce
         (fn [v {:keys [rule-name conditions then-body arg]}]
           (let [;; the rule name is a simple symbol,
                 ;; so create a qualified keyword to use as the rule name.
                 ;; this is necessary so rules with the same name can be
                 ;; created in different namespaces.
                 rule-key (keyword (str *ns*) (name rule-name))
                 rule-str (-> rule-key symbol str)
                 invalid-options #{:then}
                 has-conditions? (not (empty? conditions))]
             ;; throw if any of the conditions is using an invalid option
             (when-let [opt-name (some (fn [condition]
                                         (some invalid-options (-> condition :opts keys)))
                                       conditions)]
               (throw (ex-info (str rule-str " may not use the " opt-name " option") {})))
             ;; generate the rum component and Rule record
             (conj v `(let [*match# (clojure.core/atom nil)]
                        (rum/defc ~rule-name ~'< (reactive ~rule-key *match#) [prop#]
                          ;; throw if the rule has a :what block but no complete match
                          (when (and ~has-conditions? (not o/*match*))
                            (throw (ex-info (str ~rule-str " cannot render because the :what block doesn't have a complete match yet") {})))
                          ;; return the body of the component
                          (let [~arg o/*match*]
                            ~@then-body))
                        (o/->Rule ~rule-key
                                  (mapv o/map->Condition '~conditions)
                                  nil
                                  (fn [arg#]
                                    (if *matches*
                                      (vswap! *matches* assoc ~rule-key arg#)
                                      (reset! *match# arg#)))
                                  nil)))))
         [])
       (list 'do
         `(declare ~@(keys rules))))))
