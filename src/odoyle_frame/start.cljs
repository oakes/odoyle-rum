(ns odoyle-frame.start
  (:require [odoyle-frame.core :as c]
            [rum.core :as rum]))

(def *state (atom {:text "Hello, world!"}))

(rum/mount (c/app *state) (js/document.querySelector "#app"))
