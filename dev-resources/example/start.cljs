(ns example.start
  (:require [example.core :as c]
            [rum.core :as rum]))

(rum/hydrate (c/app) (js/document.querySelector "#app"))
