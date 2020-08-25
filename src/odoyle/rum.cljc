(ns odoyle.rum
  (:require [odoyle.rules :as o]
            [rum.core :as rum]))

(def ^:dynamic *local* nil)

#?(:clj (defn rand-uuid []
          (.toString (java.util.UUID/randomUUID)))
   :cljs (def rand-uuid random-uuid))

(defn local-state []
  (or *local* (throw (ex-info "The local-state function must be called during render." {}))))

(defmacro compset
  [rules]
  (reduce
    (fn [v {:keys [rule-name fn-name conditions then-body when-body arg]}]
      (conj v `(let [*state# (atom nil)
                     global-key# (rand-uuid)]
                 (rum/defc ~(-> rule-name name symbol)
                   ~'<
                   {:init
                    (fn [state# props#]
                      (let [local# (atom nil)]
                        (when-let [comp# (:rum/react-component state#)]
                          (add-watch *state# global-key#
                                     (fn [_# _# p# n#]
                                       (when (not= p# n#)
                                         (.forceUpdate comp#))))
                          (add-watch local# ::local
                                     (fn [_# _# p# n#]
                                       (when (not= p# n#)
                                         (.forceUpdate comp#)))))
                        (assoc state# ::local-state local#)))
                    :wrap-render
                    (fn [render-fn#]
                      (fn [state#]
                        (binding [*local* (::local-state state#)]
                          (render-fn# state#))))
                    :will-unmount
                    (fn [state#]
                      (remove-watch *state# global-key#)
                      (remove-watch (::local-state state#) ::local)
                      (dissoc state# ::local-state))}
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
