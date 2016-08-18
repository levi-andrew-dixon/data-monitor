(ns data-monitor.lib.aws.ec2
  (:require
    [clojure.java.io        :as jio]
    [clojure.tools.logging  :as log]
    [clojure.pprint         :as pp]
    [clojure.java.shell     :as sh]
    [clojure.string         :as string]
  
      
    [me.raynes.fs           :as fsu]
    [aero.core              :as aero]
  )       
)

; Lookup beetl ip, etc.

(search-instances
  [search-criteria-map]
)

(get-instance-meta
  [instance-name instance-filters]

  (try
  ;(pp/pprint (aws-ec2/describe-instances))
  (let [
    ;ec2-instances (aws-ec2/describe-instances :filters [{:name "tag:env" :values ["production"]} {:name "tag:name" :values [#"beetl-d0production.*"]}])
    ;ec2-instances (aws-ec2/describe-instances :filters [{:name "tag:name" :values ["beetl-d0production-"]}])
    ec2-instances (aws-ec2/describe-instances :filters [{:name "tag:env" :values ["production"]} {:name "tag:name" :values [#"beetl-d0production.*"]}])
    ]
    ; If instance name is a regex pattern, get entire list and return instances with matching instance names
    (when (or
      (= (class instance-name) "class java.util.regex.Pattern")
      (= (class instance-name) "class java.util.regex.Matcher"))  

    )
    (pp/pprint "beetl instance")
    (pp/pprint ec2-instances)
  )
  (catch Exception e
    (log/error "Unable to query ec2 instances: " (.getMessage e))
    (throw e)))
)
