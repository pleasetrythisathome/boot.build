(ns pleasetrythisathome.build
  {:boot/export-tasks true}
  (:require [pleasetrythisathome.deps :refer [deps]]
            [boot.core :refer :all]
            [boot.task.built-in :refer :all]
            [boot.file :as file]
            [boot.pod  :as pod]
            [boot.util :as util]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

;; ========== Version ==========

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?]
        (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                          (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags"))))]
    (cond
      dirty? (str (next-version version) "-" hash "-dirty")
      (pos? (Long/parseLong commits)) (str (next-version version) "-" hash)
      :otherwise version)))

(deftask show-version
  "Show version"
  []
  (println (deduce-version-from-git)))

;; ========== Deps ==========

(defn join-keys
  [ks]
  (->> ks
       (map name)
       (str/join "-")
       keyword))

(defn expr->ks
  [expr]
  (cond
    (keyword? expr) [[expr]]
    (map? expr) (mapcat identity
                        (for [[k vals] expr]
                          (mapv (partial vector k) vals)))
    (vector? expr) expr))

(defn pull->ks
  [expr]
  (mapcat expr->ks expr))

(defn flatten-vals
  "takes a hashmap and recursively returns a flattened list of all the values"
  [coll]
  (if ((every-pred coll? sequential?) coll)
    coll
    (mapcat flatten-vals (vals coll))))

(defn pull-deps
  ([deps expr] (pull-deps deps nil expr))
  ([deps scope expr]
   (cond->> (->> expr
                 pull->ks
                 (mapv (fn [ks]
                         (let [v (get-in deps ks)]
                           (assert v (str "missing dep: " ks))
                           v)))
                 (mapcat flatten-vals)
                 (into []))
     scope (mapv #(conj % :scope scope)))))

(defn scope-as
  "Modify dependency co-ords to have particular scope.
   Assumes not currently scoped"
  [scope deps]
  (for [co-ords deps]
    (conj co-ords :scope scope)))

(defn make-pod [deps]
  (-> (get-env)
      (update :dependencies into (vec (seq deps)))
      (pod/make-pod)
      (future)))

(defn ensure-deps!
  [pull-expr]
  (some->> pull-expr
           (pull-deps deps)
           (remove pod/dependency-loaded?)
           seq
           (scope-as "test")
           (merge-env! :dependencies)))

;; ========== Dev ==========

(deftask nrepl
  "start a nrepl server"
  []
  (comp
   (repl :server true)
   (watch)))

(deftask cider
  "CIDER profile"
  [j cljs bool "include clojurescript?"]
  (require 'boot.repl)
  (require '[boot.pod  :as pod])
  (swap! @(resolve 'boot.repl/*default-dependencies*)
         concat '[[org.clojure/tools.nrepl "0.2.12"]
                  [org.clojure/tools.namespace "0.3.0-alpha3"]
                  [cider/cider-nrepl "0.15.0-SNAPSHOT"
                   :exclusions [org.clojure/tools.reader
                                org.clojure/java.classpath]]
                  [refactor-nrepl "2.3.0-SNAPSHOT"
                   :exclusions [org.clojure/tools.nrepl]]])
  (swap! @(resolve 'boot.repl/*default-middleware*)
         concat '[cider.nrepl/cider-middleware
                  refactor-nrepl.middleware/wrap-refactor])
  (when cljs
    (swap! @(resolve 'boot.repl/*default-dependencies*)
           concat '[[com.cemerick/piggieback "0.2.1"
                     :exclusions [org.clojure/clojure
                                  org.clojure/clojurescript]]])
    (swap! @(resolve 'boot.repl/*default-middleware*)
           concat '[cemerick.piggieback/wrap-cljs-repl]))
  identity)

;; ========== Testing ==========

(deftask test-clj
  "test clj"
  []
  (set-env! :source-paths #(conj % "test"))
  (ensure-deps! [{:boot [:test]}])
  (require 'adzerk.boot-test)
  (let [test (resolve 'adzerk.boot-test/test)]
    (comp
     (test))))

(deftask test-cljs
  "test cljs"
  []
  (set-env! :source-paths #(conj % "test"))
  (ensure-deps! [{:boot [:cljs-test]}])
  (require 'crisptrutski.boot-cljs-test)
  (let [test-cljs (resolve 'crisptrutski.boot-cljs-test/test-cljs)]
    (comp
     (test-cljs))))

(deftask test-all
  "test all"
  []
  (comp
   (test-clj)
   (test-cljs)))

;; ========== Deploy ==========

(deftask add-file
  "add deployment files to fileset"
  [f path PATH str "the path to the file"
   t target PATH str "the target in the fileset"]
  (let [tgt (tmp-dir!)
        add-files
        (delay
         (let [file (io/file path)
               target (or target (.getName file))]
           (util/info (str "Adding " path " to fileset as " target "...\n"))
           (file/copy-with-lastmod file (io/file tgt target))))]
    (with-pre-wrap fileset
      @add-files
      (-> fileset (add-resource tgt) commit!))))
