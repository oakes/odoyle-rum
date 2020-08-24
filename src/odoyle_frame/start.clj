(ns odoyle-frame.start
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :refer [not-found]]
            [clojure.java.io :as io]
            [rum.core :as rum]
            [odoyle-frame.core :as c]
            [clojure.string :as str])
  (:gen-class))

(def port 3000)

(defmulti handler (juxt :request-method :uri))

(defmethod handler [:get "/"]
  [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (-> "public/index.html" io/resource slurp
             (str/replace "{{app}}" (rum/render-html (c/app (atom {:text "Test"})))))})

(defmethod handler :default
  [request]
  (not-found "Page not found"))

(defn run-server [handler-fn]
  (run-jetty (-> handler-fn
                 (wrap-resource "public")
                 wrap-content-type)
             {:port port :join? false})
  (println (str "Started server on http://localhost:" port)))

(defn -main [& args]
  (run-server handler))

