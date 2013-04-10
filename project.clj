(defproject sudoku "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [ring-mock "0.1.3"]
                 [cheshire "5.0.2"]]
  :plugins [[lein-ring "0.8.3"]]
  :ring {:handler sudoku.web/app})
