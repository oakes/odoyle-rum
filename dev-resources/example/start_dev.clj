(ns example.start-dev
  (:require [example.start :as start]
            [clojure.spec.test.alpha :as st]
            [ring.middleware.file :refer [wrap-file]]
            [clojure.java.io :as io]))

(defn -main []
  (st/instrument)
  (st/unstrument 'odoyle.rules/insert) ;; don't require specs for attributes
  (.mkdirs (io/file "target" "public"))
  (-> start/handler
      (wrap-file "target/public")
      start/run-server))
