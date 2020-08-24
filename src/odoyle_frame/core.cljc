(ns odoyle-frame.core
  (:require [rum.core :as rum]))

(rum/defc repeat-label [n text]
  [:div (replicate n [:.label text])])
