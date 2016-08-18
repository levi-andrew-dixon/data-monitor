(defproject data-monitor "0.1.4-SNAPSHOT"

  :description  "CLI tools and libraries for monitoring and alerting on data integrity issues (e.g. anomolous or missing data)"
  :url          "https://github.com/shopsmart/data-monitor"

  :dependencies [
    ; ==== Clojure standard libs ====;

    ; NOTE: Due to the amount of work required to effectively model and implement [data structure] schemas,
    ;       I have preferred to use the alphas clojure release containing clojure.spec. 
    [org.clojure/clojure        "1.9.0-alpha10"]
    [org.clojure/tools.logging  "0.3.1"]
    [org.clojure/tools.nrepl    "0.2.11"]
    [org.clojure/tools.cli      "0.3.5"]                ; Arg processing

    ; ==== BD libs ==== ;

    ; ==== 3rd party libs ==== ;

    [org.apache.logging.log4j/log4j-api     "2.3"]      ; Logging
    [org.apache.logging.log4j/log4j-core    "2.3"]      ; Logging
    [org.apache.logging.log4j/log4j-1.2-api "2.3"]      ; Logging

    [aero                                   "1.0.0"]    ; Extended EDN processing
    [clj-logging-config                     "1.9.12"]
    [com.draines/postal                     "2.0.0" ]   ; Email lib
    [me.raynes/fs                           "1.4.6"]    ; FS utils
    [org.postgresql/postgresql              "9.4.1209"] ; JDBC driver
    [com.h2database/h2                      "1.4.192"]  ; JDBC driver
    [pdclient                               "0.1.3"]    ; PagerDuty (alerting/on-call system)
    ; [clojurewerkz/quartzite                 "2.0.0"]  ; Scheduler
    [clj-ssh                                "0.5.14"]   ; SSH via jsch
    [zookeeper-clj                          "0.9.4"]    ; Zookeeper

    ; ==== AWS libs ==== ;

    [clj-http                                 "3.1.0"]
    [com.fasterxml.jackson.core/jackson-core  "2.8.1"]    ; AWS lib depends on this, but MVN deps don't resolve it
    [amazonica                                "0.3.74"]]

  ;:main         ^:skip-aot data-monitor.core
  :main         data-monitor.core
  :target-path  "target/%s"

  :profiles { 
    :uberjar {:aot :all}
    
    ; Default profile if none is specified
    :dev {
      :jvm-opts [
        ; Override log4j2 log level
        "-Dlog4j.configurationFile=log4j2.dev.xml"]}

    :debug {
      :jvm-opts [
        ; Setup remote tracing capabilities
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5010"
        ; Override log4j2 log level
        "-Dlog4j.configurationFile=log4j2.debug.xml"]}}

  :aliases {
    "debug" ["with-profile" "debug" "run"]}

  :plugins [
    ; exec non "core.clj" or project main class .clj files as CLI programs
    ; e.g. lein exec release-util.clj
    [lein-exec                                  "0.3.6"]

    ; (URL: https://github.com/hyPiRion/lein-shell)
    ; Run shell commands as release-task steps
    [lein-shell                                 "0.5.0"]

    ; (URL: https://github.com/technomancy/leiningen/tree/master/lein-pprint)
    ; Support pprint of project metadata
    ; e.g. lein pprint release-tasks
    [lein-pprint                                "1.1.2"]

    ; SSH deployment target support
    [org.apache.maven.wagon/wagon-ssh-external  "2.6"]

    ; S3 deployment target support
    [s3-wagon-private                           "1.2.0"]]

  ; General usage repositories (used for dependency resolution as well as deploy / release destinations)
  :repositories [
    ; SCP release target
    ; ["releases" "scp://somerepo.com/home/repo/"]

    ["snapshots" {
      :id             "bd-data-services-snapshots"
      :url            "s3://data-services-assets/deployment/jar/snapshots/" 
      :sign-releases  false}]
    ["releases" {
      :id             "bd-data-services-releases"
      :url            "s3://data-services-assets/deployment/jar/releases/" 
      :sign-releases  false}]]

  ; Override release-tasks in order to modify tagging step (add prefix "v" to tag name created from version)
  :release-tasks [
    ["vcs"    "assert-committed"]
    ["change" "version" "leiningen.release/bump-version" "release"]
    ["vcs"    "commit"]
    ["vcs"    "tag" "v" "--no-sign"]
    ["deploy"]
    ["change" "version" "leiningen.release/bump-version"]
    ["vcs"    "commit"]
    ["vcs"    "push"]])
