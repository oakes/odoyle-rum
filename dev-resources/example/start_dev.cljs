(ns example.start-dev
  (:require [example.start]
            [clojure.spec.test.alpha :as st]))

(st/instrument)
(st/unstrument 'odoyle.rules/insert) ;; don't require specs for attributes
