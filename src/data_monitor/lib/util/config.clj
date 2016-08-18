(ns data-monitor.lib.util.config
  (:require 
    [clojure.java.io        :as jio]
    [clojure.tools.logging  :as log]
    [clojure.pprint         :as pp]
    [clojure.java.shell     :as sh]
    [clojure.string         :as string]

    [me.raynes.fs           :as fsu]
    [aero.core              :as aero])
  (:import
    [java.lang      Thread]
    [java.util.jar  JarFile]
    [java.net       JarURLConnection URL URI])
  (:gen-class))

; Get namespace at compile time
(def lib-ns       *ns*)
(def lib-ns-name  (ns-name lib-ns))

;
; Helper functions for Aero custom tag handling
;

(defn jar-resource-path-split
  "Takes a jar / resource URI path (e.g. 'jar:file:/Users/levidixon/git/shopsmart/data-monitor/target/uberjar/data-monitor-0.1.0-standalone.jar!/config/config.edn')
   and splits it into jar path and resource path.

   NOTE: If the path passed in contains a URI 'file:' specifier, it will be passed back out in the jar-path.

   @return path-pieces-vec  [jar-path resource-path]"
  [path]
  (let [str-path  (str path)]
    (if-let [path-pieces-vec (string/split str-path #"!" )]
      path-pieces-vec
      [str-path])))


(defn expand-relative-path
  "Given two paths, return a full path representation of the 2nd path as relative to the 1st path.
   This can be used to resolve the relative path of files included from another (in a jar file
   or otherwise).

   E.g. [\"/config/config.edn\" \"secret/env_setup.edn\"] --> \"/config/secret/env_setup.edn\"

   @return  expanded-path   The resolved / expanded path."
  [source-path include-path & [will-output-abs-path]]
  (let [
    source-resource-path-pieces-vec (fsu/split  source-path)
    include-path-pieces-vec         (fsu/split  include-path)
    source-base-path-vec            (butlast source-resource-path-pieces-vec)
    expanded-path
      (if will-output-abs-path
        (string/join "/"
          (concat
            (if (not (= (first source-resource-path-pieces-vec) "/"))
              (concat ["/"] source-base-path-vec)
              source-base-path-vec)
            include-path-pieces-vec))
        (string/join "/"
          (concat
            (if (= (first source-resource-path-pieces-vec) "/")
              (rest source-base-path-vec)
              source-base-path-vec)
            include-path-pieces-vec)))]
      expanded-path))


(defn custom-resource-resolver
  "Resolves file paths as JAR resources, such that they may be included when the application is packaged as a JAR.
   This function may be used by the config library to resolve top level config file paths and sub-include relative file paths."
  [source include]

  ; If source is nil assume this is a top-level include; otherwise assume it is an include from the file contained in source
  ; For top level includes; remove the "jar:" URI type indicator; so that (file include) will return a file handle to the file named
  ; Otherwise, make the sub-include relative to the source file and wrap it in (jio/resource include), so it will be loaded as a resource properly
  (if source
    (do
      (if (re-find #"jar:" (str source))
        (let [
          [jar-path resource-path]  (jar-resource-path-split source)
           expanded-path            (expand-relative-path resource-path include)]
          (log/debug (format "Expanded relative path; source path [%s], target path [%s], expanded path [%s]" jar-path resource-path expanded-path))
          (jio/resource expanded-path))
          (jio/resource include)))

    ; This handles top level configuration files
    (do
      (if (re-find #"jar:" (str include))
        (let [
          [jar-path resource-path]  (jar-resource-path-split include)]
          (log/debug (format "Converted jar --> resource path ; source path [%s], target path [%s], chopped path [%s]" jar-path resource-path resource-path))
          (jio/resource resource-path))))))    


(defn get-resources-enum
  "Get a list of resources from the supplied path.

   @param   path            A path to a JAR resource.
   @return  resources-enum  An enumeration of the matching resources."
  [& [path]]
  (let [path (if path path "")]
    (-> (Thread/currentThread) (.getContextClassLoader) (.getResources path))
  )
)

(defn get-resources
  "Get a list of resources from the supplied path.

   @param   path            A path to a JAR resource.
   @return  resource-seq    A sequence of the matching resources."
  [& [path]]
  (let [path (if path path "")]
    (enumeration-seq (get-resources-enum path))
  )
)

(defn get-current-class-load-path
  "The location from which this class was loaded (Filesystem path, JAR URI, etc.)"
  [& [ns]]
  (let [ns (if ns ns *ns*)]
    (let [curr-class (-> (Thread/currentThread) (.getContextClassLoader) (.getResource (str ns)))]
      curr-class
    )
  )
)

(defn get-jar-files
  "Get a list of top level JAR files / directories.  Not recursive."
  []
  (let [resources-enum (get-resources-enum "")]
    (when (.hasMoreElements resources-enum)
      (let [
        url           ^URL (.nextElement resources-enum)
        url-path      (string/replace (.getPath url) #"(.*/).*/$" "$1")
        url-res       (jio/resource url-path)
        url-res-file  (jio/file (jio/resource url-path))
        url-file      (jio/file url-path)
        url-conn      ^JarURLConnection (.openConnection (URL. (str "file://" url-path)))
        ]

        (try
          (let [
            jar   ^JarFile (.getJarFile url-conn)]
            (doall (map #((println "jar entry: " %)(println "entry name: " (.getName %)) (enumeration-seq (.entries jar)))))
          )
        (catch Exception e
          (ex-info "Error getting jar files: " {:orig-message (.getMessage e) :ex e}))
        )
      )
    )
  )
)

;
; Setup Aero custom tag handlers (reader mutimethod functions must be defined before config file is processed)
;

; TODO: Setup custom file reader instead of depending on multimethod

(defmethod aero/reader 'shell
  ;[{:keys [profile] :as opts} tag value]
  [opts tag tag-val]
  (try 
  ;(let [ret-val  (sh/sh (string/split tag-val #"\s+"))]
  (let [ret-val  (apply sh/sh tag-val)]
    (ret-val :out))
  (catch Exception e
    (str "ERROR running shell command: " (.getMessage e))))
)

;
; Config library begin
;

(defn read-conf-resource
  [file-path & [{:keys [profile-name resolver], :as opt-map}]]
  (try
    (let [
      conf-reader-opt-map
        (merge 
          {}
          (when profile-name  {:profile   (keyword profile-name)})
          (when resolver      {:resolver  resolver}))
      ]
      (aero/read-config (jio/resource file-path) conf-reader-opt-map)
    )

    (catch Exception e
      (log/error "Error loading configuration file: " (.getMessage e))
      (throw e))
  )
)
  

(defn get-val
  "Given a map with configuration, retrieves the the supplied nested value

  @param  conf-map      A map with configuraiton data.
  @param  key-name-vec  A vector with keynames denoting the nested path of the value to be retrieved.

  @return val           The referened value or nil"
  [conf-map key-name-vec]
  (get-in conf-map key-name-vec))


; Create a memoized version of the get-val function to cache values read from config
; (i.e. prevent accessing disk for values already read)
(def get-val-memoized-fn (memoize get-val))


(defn get-multi-memoized
  "Takes a list of vectors, each element as expected input for get-val.

  @param  conf-map            A map containing confuration
  @param  key-name-vec-list   A list of key-name vectors (e.g. [:task-id :task-name])
  @param  prefix-key-vec      A list of key-names which will be used as a prefix  each key-name-vec processed (e.g. [:tasks :sql])
  @see    get-val
  @return val-vec             A LAZY vector containing configuration retrieved for each supplied input key-name vec"
  [conf-map key-name-vec-list & [start-key-vec]]
  (map (fn [key-name-vec] (get-val-memoized-fn conf-map (concat start-key-vec key-name-vec))) key-name-vec-list))
