(ns sudoku.web
  (:use compojure.core)
  (:require [clojure.java.io :as io]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [sudoku.core :as sudoku]))

(defn wrap-json [h]
  (fn [req]
    (let [json-resp (fn [res data]
                      (-> res
                          (resp/content-type "application/json")
                          (assoc :body (json/encode data))))
          ctype     (:content-type req)]
      (if (and ctype (.startsWith ctype "application/json"))
        (with-open [r (io/reader (:body req))]
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

(defn init-session
  ([] (init-session :moderate))
  ([level] (let [board    (sudoku/random-board level)
                 solution (sudoku/solve board)]
             {:orig-board board
              :originals  (sudoku/non-blank-cells board)
              :board      board
              :solution   solution
              :difficulty level
              :hints      #{}
              :errors     #{}
              :solved     false})))

(defn game-state [session]
  (dissoc session :orig-board :solution))

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
        solved    (sudoku/solved? board)
        session   (-> session
                      (assoc :board board)
                      (assoc :errors errors)
                      (assoc :solved solved))
        resp      {:validMove (boolean valid?)
                   :solved    solved}]
    (-> (send-json resp)
        (assoc :session session))))

(defn get-hint [session]
  (let [board     (:board session)
        hints     (:hints session)
        solution  (:solution session)
        blanks    (sudoku/blank-cells board)
        loc       (rand-nth blanks)
        val       (get-in solution loc)
        board     (assoc-in board loc val)
        hints     (conj hints loc)
        solved    (sudoku/solved? board)
        resp      {:location loc :value val :solved solved}
        session   (-> session
                      (assoc :board board)
                      (assoc :hints hints)
                      (assoc :solved solved))]
    (-> (send-json resp)
        (assoc :session session))))

(defn reset-board [session]
  (let [session (-> session
                    (assoc :board (:orig-board session))
                    (assoc :hints #{})
                    (assoc :errors #{})
                    (assoc :solved false))]
    (-> (resp/response "ok")
        (assoc :session session))))

(defn new-game [json-data session]
  (let [level   (:difficulty json-data)
        level   (keyword level)
        session (init-session level)]
    (-> (resp/response "ok")
        (assoc :session session))))

(defn check-board [session]
  (let [board     (:board session)
        solution  (:solution session)
        errors    (filter (fn [loc]
                            (let [v  (get-in board loc)
                                  sv (get-in solution loc)]
                              (and (pos? v) (not= v sv))))
                          sudoku/all-locations)
        errors    (set errors)
        session   (assoc session :errors errors)]
    (-> (resp/response "ok")
        (assoc :session session))))

(defn send-to [loc]
  (io/resource (str "public/" loc)))

(def sudoku-routes
  (-> (context "/sudoku" []
               (POST "/newGame" {:keys [json-data session]}
                     (new-game json-data session))
               (POST "/getGameState" {:keys [session]}
                     (send-json (game-state session)))
               (POST "/updateBoard" {:keys [json-data session]}
                     (update-board json-data session))
               (POST "/getHint" {:keys [session]}
                     (get-hint session))
               (POST "/resetBoard" {:keys [session]}
                     (reset-board session))
               (POST "/checkBoard" {:keys [session]}
                     (check-board session))
               (GET "/" []
                    (send-to "index.html"))
               (GET "" []
                    (send-to "index.html")))
      ensure-session))

(defroutes app-routes
  (route/resources "/")
  sudoku-routes
  (route/not-found (send-to "404.html")))

(def app (-> app-routes
             wrap-json
             handler/site))
