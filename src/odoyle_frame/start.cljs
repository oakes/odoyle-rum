(ns odoyle-frame.start
  (:require [odoyle-frame.core :as c]
            [rum.core :as rum]))

(rum/mount (c/repeat-label 5 "abc") (js/document.querySelector "#content"))
