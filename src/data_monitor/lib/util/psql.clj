(ns data-monitor.lib.util.psql
  (:require 
    [clojure.java.shell     :as sh]
    [clojure.pprint         :as pp]
    [clojure.tools.logging  :as log]
    [clojure.string         :as string])
  (:import
    [java.lang  Runtime]
    [java.io    InputStreamReader OutputStreamWriter BufferedReader BufferedWriter])
  (:gen-class)
)

; TODO: Add functions to work with output of another program piped to STDIN of this program (System.in)

(defn db-details-to-psql-arg-list-vec
  "Transforms a map with db details (@see jdbc clj wrapper) into command line arguments"
  [db-details-map & [additional-arg-vec]]
  (let [cli-arg-string
      (concat (when additional-arg-vec additional-arg-vec)
        (if-let [arg-db (db-details-map :db)]
            ["-d" arg-db])
        (if-let [arg-host (db-details-map :host)]
            ["-h" arg-host])
        (if-let [arg-user (db-details-map :user)]
            ["-U" arg-user])
        (if-let [arg-port (db-details-map :port)]
            ["-p" (db-details-map :port)]))]
    cli-arg-string
  )
)

(defn db-details-to-psql-arg-list-string
  "Transforms a map with db details (@see jdbc clj wrapper) into command line arguments"
  [db-details-map & [additional-arg-vec]]
  (db-details-to-psql-arg-list-vec db-details-map additional-arg-vec)
)

(defn extract-num-rows-from-result-string
  "Extracts the number of rows returned for each query run.

  @return num-rows-vec  A vector containing the number of rows returned."
  [psql-output]
  ;(doall (map (fn [x] (Integer/parseInt (nth x 3))) (re-seq #"(\n|\<p\>){0,1}\((\d*) rows{0,1}\)" psql-output)))
  (doall (map (fn [x] (Integer/parseInt (second x))) (re-seq #"\n.*?\((\d*) rows{0,1}\)" psql-output)))
)

(defn wrap-sql-block
  "Wraps SQL text with header and footer SQL or psql commands (such as header \"SET query_group = blah;\" and footer \"\\q\" to quit"
  [sql & [header-cmd-string-vec footer-cmd-string-vec]]
  (if (or header-cmd-string-vec footer-cmd-string-vec)
    (string/join "\n" (concat header-cmd-string-vec [sql] footer-cmd-string-vec))
    sql))

