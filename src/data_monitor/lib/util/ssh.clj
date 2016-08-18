(ns data-monitor.lib.util.ssh
  "Utils to work with SSH tasks and remote command output"
  (:require 
    ; Clojure stdlib
    [clojure.java.io            :as jio]
    [clojure.java.shell         :as sh]
    [clojure.pprint             :as pp]
    [clojure.string             :as string]
    [clojure.tools.logging      :as log]

    ; 3rd party lib
    [clj-ssh.cli                :as ssh-cli]
    [clj-ssh.ssh                :as ssh]
  )
  (:gen-class))


(defn remote-exec
  "@param   remote-command  Either a scalar with a single command or a vector with multiple commands.
   @return  output          Either a scalar with a single commands output or a vector for each command in remote-command vector" 
  [{:keys [username host remote-command], :as opt-map}]

  (let [
    result
      (cond (vector? remote-command)
        (map remote-exec remote-command)
        :else
          (let [ssh-agent-ref (ssh/ssh-agent {})]
            (ssh/add-identity ssh-agent-ref {
              :name             "production-01.pem"
              :private-key-path "/Users/levidixon/.ssh/production-01.pem"})
            (let [session (ssh/session ssh-agent-ref host {:username username :strict-host-key-checking :no})]
              (ssh/ssh session {
                 :cmd      remote-command
                 :username username }))))]
    result))

