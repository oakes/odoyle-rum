(ns odoyle-frame.core
  (:require [rum.core :as rum]))

(rum/defc app < rum/reactive [*state]
  (let [{:keys [text]} @*state]
    [:div text]))
