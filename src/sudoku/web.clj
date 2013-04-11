(ns sudoku.web
  (:use [clojure.java.io :only (reader)]
        compojure.core
        [ring.util.response :only (content-type response)]
        ring.adapter.jetty)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [sudoku.core :as sudoku]))

(defn wrap-json [h]
  (fn [req]
    (let [json-resp (fn [res data]
                      (-> res
                          (content-type "application/json")
                          (assoc :body (json/encode data))))
          ctype     (:content-type req)]
      (if (= ctype "application/json")
        (with-open [r (reader (:body req))]
          (let [req (assoc req :json-data (json/decode-stream r true))
                res (h req)]
            (if-let [data (:json-data res)]
              (json-resp res data)
              res)))
        (let [res (h req)]
          (if-let [data (:json-data res)]
            (json-resp res data)
            res))))))

(defn send-json [data]
  {:json-data data})

(defn init-session []
  (let [board  (sudoku/random-board)
        solved (sudoku/solve board)]
    {:orig-board board
     :board      board
     :solved     solved
     :hints      #{}
     :errors     #{}}))

(defn ensure-session [h]
  (fn [req]
    (let [session (:session req)
          board   (:board session)]
      (if-not (and session board)
        (-> (resp/redirect (:uri req))
            (assoc :session (init-session)))
        (h req)))))

(defn update-board [data session]
  (let [location  (:location data)
        value     (:value data)
        board     (:board session)
        errors    (:errors session)
        valid?    ((sudoku/can-have location board) value)
        errors    (if (or valid? (zero? value))
                    (disj errors location)
                    (conj errors location))
        board     (assoc-in board location value)
        session   (-> session
                      (assoc-in [:board] board)
                      (assoc-in [:errors] errors))
        resp      {:validMove (boolean valid?)
                   :solved    (sudoku/solved? board)}]
    (-> (send-json resp)
        (assoc :session session))))

(defn get-hint [session]
  (let [board   (:board session)
        solved  (:solved session)
        blanks  (sudoku/blank-cells board)
        loc     (rand-nth blanks)
        [r c]   loc
        val     (get-in solved loc)
        resp    {:location loc :value val}
        session (-> session
                    (assoc-in [:board r c] val)
                    (update-in [:hints] #(conj % loc)))]
    (-> (send-json resp)
        (assoc :session session))))

(defn reset-board [session]
  (let [session (-> session
                    (assoc :board (:orig-board session))
                    (assoc :hints #{})
                    (assoc :errors #{}))]
    (-> (resp/response "ok")
        (assoc :session session))))

(defn check-board [session]
  (let [board      (:board session)
        solved     (:solved session)
        errors     (filter (fn [loc]
                             (let [v  (get-in board loc)
                                   sv (get-in solved loc)]
                               (and (pos? v) (not= v sv))))
                           sudoku/all-locations)
        errors     (set errors)
        session    (assoc session :errors errors)]
    (-> (resp/response "ok")
        (assoc :session session))))

(defroutes app-routes
  (POST "/getBoard" {:keys [session]}
        (send-json (:board session)))
  (POST "/getOriginals" {:keys [session]}
        (send-json (sudoku/non-blank-cells (:orig-board session))))
  (POST "/updateBoard" {:keys [session json-data]}
        (update-board json-data session))
  (POST "/getHint" {:keys [session]}
        (get-hint session))
  (POST "/getHints" {:keys [session]}
        (send-json (:hints session)))
  (POST "/getErrors" {:keys [session]}
        (send-json (:errors session)))
  (POST "/resetBoard" {:keys [session]}
        (reset-board session))
  (POST "/checkBoard" {:keys [session]}
        (check-board session))
  (GET "/" []
        (resp/redirect "/index.html"))
  (route/resources "/")
  (route/not-found (resp/resource-response "404.html" {:root "public/"})))

(def app (-> app-routes
             wrap-json
             ensure-session
             handler/site))