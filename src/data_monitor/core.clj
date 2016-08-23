(ns data-monitor.core
  (:require 

    ; External lib dependencies
    [clojure.java.io                  :as jio]
    [clojure.java.shell               :as sh]
    [clojure.pprint                   :as pp]
    [clojure.tools.logging            :as log]
    [clojure.tools.nrepl.server       :as nrepl]
    [clojure.string                   :as string]

    ; Dependencies on libs contained in this app
    [data-monitor.lib.aws.ec2         :as ec2]
    [data-monitor.lib.util.alert      :as alert]
    [data-monitor.lib.util.config     :as conf]
    [data-monitor.lib.util.email      :as email]
    [data-monitor.lib.util.psql       :as psql]
    [data-monitor.lib.util.ssh        :as ssh]

    ; 3rd party libs
    [aero.core                        :as aero]

    [clojure.xml :as xml]
    [clojure.zip :as zip])

  (:gen-class))


(defonce nrepl-port 7890)


(defn- construct-context
  "Read configuration and setup a run-time environment for tracking state

   @return ctx-map-atom   Returns a reference to an atom containing a map with current configuration and state."
  [& [config-map]]
  (let [ctx-atom (atom (merge { :exception-vec [] } { :config-map config-map }))]
    ctx-atom
  )
)


(defn process-sql-task-results
  [ctx-atom task-map file-name query-results num-rows-vec]
  (let [
    conf-map (@ctx-atom :config-map)]

    (when (> (first num-rows-vec) 0)
      (log/info "Assertion SQL returned more than 1 row; emailing error details")
      (doall
        (map
          (fn [alert-action-map]
            (alert/process-alert
              conf-map
              alert-action-map {
                :subject
                  (str "FILE [" file-name "]")
                :content-type
                  "text/html"
                :body
                  (format
                    (str "<h2>== DATA MONITORING FAILURE (%s) ==</h2>\n\n"
                      query-results)
                    file-name)}))
            (conf/get-val-memoized-fn task-map [:alert-vec]))))))


(defn process-ssh-task-results
  "Process results of SSH monitoring task"
  [ctx-atom task-map ret-val]
  (let [
    conf-map  (@ctx-atom :config-map)
    out-lines (string/split-lines (ret-val :out))]

  (log/debug "Processing SSH task results")

  (when (> (count out-lines) 1)
    (doall (map
      (fn [alert-action-map]
        (alert/process-alert
          conf-map
          alert-action-map {
            :subject
              (str "Task name [" (task-map :task-name) "]")
            :content-type
              "text/html"
            :body
              (str
                (format "<body><h2>== DATA MONITORING FAILURE (%s) ==</h2>\n\n<pre>" 
                  (or (task-map :remote-command) (task-map :file-name)))
                (ret-val :out)
                "<pre></body>") }))
        (conf/get-val-memoized-fn task-map [:alert-vec]))))))


(defn- process-sql-task
  "Process a single SQL monitoring task"
  [ctx-atom task-map]

  (log/debug "Processing SQL task-map: " task-map)

  (let [
    conf-map (@ctx-atom :config-map)]

    (try
      (cond
        (= (task-map :exec-method) "psql")
          (let [
            psql-path           (conf/get-val-memoized-fn conf-map  [:psql-path])
            sql-file-name       (conf/get-val-memoized-fn task-map  [:file-name])
            sql-file-in-stream  (jio/input-stream (jio/resource (conf/get-val-memoized-fn task-map [:file-name])))
            db-details-map      (conf/get-val-memoized-fn conf-map [:secret :dsn :redshift-dw-prod-bdadmin])
            psql-command-vec    (concat [psql-path] (psql/db-details-to-psql-arg-list-vec db-details-map ["-q" "--variable" "ON_ERROR_STOP=1" "--html"]) [:in sql-file-in-stream])
            ret-val             (apply sh/sh psql-command-vec)
            exit-code           (ret-val :exit)
            num-rows-vec        (when exit-code > 0 (psql/extract-num-rows-from-result-string (ret-val :out)))]
          (if (= exit-code 0)
            (do
              (log/debug  "psql command completed successfully")
              (process-sql-task-results ctx-atom task-map sql-file-name (ret-val :out) num-rows-vec))
            (do
              (log/error  "psql command exited with code: " exit-code ret-val)))))

    (catch Exception e
      (throw (ex-info (str "Unable to process task: " (.getMessage e)) {:task-map task-map} e))))
  )
)

; TODO: Implement process-psql-task and process-sql-task using exec method of db-lib (using stub db API functions)

(defn- process-ssh-task
  "Process a single SSH monitoring task"
  [ctx-atom task-map]

  (log/debug "Processing SSH task-map: " task-map)

  (let [
    conf-map (@ctx-atom :config-map)
    [task-id task-name description validation-type assertion-type username remote-hostname ec2-instance ec2-instance-regex file-name remote-command exec-method]
      (conf/get-multi-memoized task-map [
          [:task-id]
          [:task-name]
          [:description]
          [:validation-type]
          [:assertion-type]
          [:username]
          [:remote-hostname]
          [:ec2-instance]
          [:ec2-instance-regex]
          [:file-name]
          [:remote-command]
          [:exec-method]])]

    (try
      (let [
        ret-val
          (ssh/remote-exec {
            :username       username
            :host
              (or remote-hostname
                  (ec2/get-private-hostname-from-instance-map
                    (first (ec2/get-instances-by-name (or ec2-instance (re-pattern ec2-instance-regex))))))
            :remote-command (or remote-command (or file-name (slurp file-name)))})]

        (log/debug "SSH ret-val: " ret-val)
        (log/debug "SSH command completed successfully")

        (process-ssh-task-results ctx-atom task-map ret-val))
  
    (catch Exception e
      (throw (ex-info (str "Unable to process task: " (.getMessage e)) {:task-map task-map} e))))))


(defn- process-monitoring-tasks
  "Process each monitoring list of tasks by type"
  [ctx-atom task-map]
  
  (let [
    conf-map (@ctx-atom :config-map)]

    (if-let [sql-tasks (task-map :sql)]
      (doall (pmap (fn [task] (process-sql-task ctx-atom task)) sql-tasks)))

    (if-let [ssh-tasks (task-map :ssh)]
      (doall (pmap (fn [task] (process-ssh-task ctx-atom task)) ssh-tasks)))))


(defn- teardown-cleanup
  "Factored out program cleanup"
  [nrepl-server]

  (log/debug "Running teardown-cleanup")

  (when nrepl-server
    (log/debug "Stopping nREPL")
    (nrepl/stop-server nrepl-server))

  ; Necessary to reap "future" threads that java/shell uses to execute programs in a separate thread (i.e. prevent hanging)
  (log/debug "Shutting down agents")
  (shutdown-agents)
)


(defn -main
  "Entry point for the CLI program"
  [& args]

  (log/info "Initializing ...")

  ; Start nREPL
  (defonce nrepl-server (nrepl/start-server :port nrepl-port))

  (let [
    config-atom   (atom nil)]

  ; Attempt to load config from file-system 
  (try
    (log/debug "Loading configuration using default file resolver ...")
    (let [
      conf-env-name     (conf/get-val-memoized-fn (conf/read-conf-resource "config/env-name.edn") [:env-name])
      conf              (conf/read-conf-resource "config/config.edn" {:profile-name conf-env-name})]
      (reset! config-atom   conf))

  ; Expected possible exception (if run from a JAR)
  (catch Exception e
    (log/debug "Exception loading config using default resolver, attempting again using custom file resolver: " (.getMessage e))))

  ; If config file was not found using FS path, use JAR URI and treat the file(s) as resource(s)
  (when (empty? (when config-atom @config-atom))
    (try
      (log/debug "Loading configuation using custom file resolver ...")
      (let [
        conf-env-name     (conf/get-val-memoized-fn (conf/read-conf-resource "config/env-name.edn" {:resolver conf/custom-resource-resolver}) [:env-name])
        conf              (conf/read-conf-resource "config/config.edn" {:profile-name conf-env-name :resolver conf/custom-resource-resolver})]
        (reset! config-atom   conf))

    ; Unable to load the config file using either method; die
    (catch Exception e
      (log/error "Exception loading config (via custom resolver): " (.getMessage e))
      (throw e))))

  (let [
    ctx-atom  (construct-context @config-atom)]

    ; Loop over monitoring tasks
    (try
      (log/info "Running data monitoring tasks ...")
      (process-monitoring-tasks ctx-atom (conf/get-val-memoized-fn @config-atom [:monitoring :tasks]))
    (catch Exception e
      (log/error "Exception while processing monitoring tasks: " (.getMessage e) e))
    (finally
      (log/debug "Running finally ...")
      (teardown-cleanup nrepl-server))))
  
  (log/info "Program finished."))
  (System/exit 0))

