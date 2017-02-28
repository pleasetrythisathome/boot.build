(ns pleasetrythisathome.build
  {:boot/export-tasks true}
  (:require [boot.core :refer :all]
            [boot.task.built-in :refer :all]
            [boot.file :as file]
            [boot.pod  :as pod]
            [boot.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

;; ========== Utils ==========

(defmacro r
  "Ensure symbol is loaded, and then resolve it. Useful with pods."
  [sym]
  `(do (require '~(symbol (namespace sym))) (resolve '~sym)))

;; ========== Version ==========

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn git-describe
  []
  (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                    (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags")))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?] (git-describe)]
    (cond
      dirty? (str (next-version version) "-" hash "-dirty")
      (and commits (pos? (Long/parseLong commits))) (str (next-version version) "-" hash)
      :otherwise (or version "0.1.0-SNAPSHOT"))))

(deftask show-version
  "Show version"
  []
  (println (deduce-version-from-git)))

(defn dep
  []
  ((juxt :project :version)
   (:task-options (meta #'pom))))

(deftask show-dep
  "Show version"
  []
  (println (pr-str (dep))))

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

(def dep-map (edn/read-string (slurp (io/resource "dependencies.edn"))))

(defn pull-deps
  ([expr] (pull-deps dep-map expr))
  ([deps expr]
   (->> expr
        pull->ks
        (mapv (fn [ks]
                (let [v (get-in deps ks)]
                  (assert v (str "missing dep: " ks "\n"))
                  v)))
        (mapcat flatten-vals)
        (into []))))

(defn scope-as
  "Modify dependency co-ords to have particular scope.
   Assumes not currently scoped"
  [scope deps]
  (mapv #(conj % :scope scope) deps))

(defn make-pod [deps]
  (-> (get-env)
      (update :dependencies into (vec (seq deps)))
      (pod/make-pod)
      (future)))

(defn ensure-deps!
  ([pull-expr] (ensure-deps! dep-map pull-expr))
  ([deps pull-expr]
   (some->> pull-expr
            (pull-deps deps)
            (remove pod/dependency-loaded?)
            seq
            (scope-as "test")
            (merge-env! :dependencies))))

(ensure-deps! [:fs])

(require '[me.raynes.fs :as fs])

;; ========== Env ==========

(defn read-deps
  [dir]
  (try (edn/read-string (slurp (io/file (str dir "/deps.edn"))))
       (catch Exception e
         (util/warn (str "missing deps.edn in dir: " dir)))))

(defn project-env
  ([] (project-env "."))
  ([root]
   (->> (for [[k dir] {:source-paths "src"
                       :resource-paths "resources"
                       :asset-paths "assets"}
              :let [dir (str root "/" dir)]
              :when (fs/exists? (io/file dir))]
          [k #{dir}])
        (into {})
        (merge {:dependencies (pull-deps (read-deps root))}))))

(defn merge-project-env!
  ([env] (merge-project-env! env false))
  ([env submodule?]
   (->> (cond-> env
          submodule? (update :dependencies (partial remove pod/dependency-loaded?)))
        (apply concat)
        (apply merge-env!))))

(defn submodules
  ([] (submodules "submodules"))
  ([root]
   (->> root
        io/file
        fs/list-dir
        (filter fs/directory?)
        (mapv (comp (partial str root "/") fs/name)))))

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
  (let [cljs? (r cemerick.piggieback/wrap-cljs-repl)]
    (swap! @(r boot.repl/*default-dependencies*)
           concat (cond-> '[[org.clojure/tools.nrepl "0.2.12"]
                            [org.clojure/tools.namespace "0.3.0-alpha3"]
                            [cider/cider-nrepl "0.15.0-SNAPSHOT"
                             :exclusions [org.clojure/tools.reader
                                          org.clojure/java.classpath]]
                            [refactor-nrepl "2.3.0-SNAPSHOT"
                             :exclusions [org.clojure/tools.nrepl]]]
                    cljs? (conj '[com.cemerick/piggieback "0.2.1"
                                  :exclusions [org.clojure/clojure
                                               org.clojure/clojurescript]])))
    (swap! @(r boot.repl/*default-middleware*)
           concat (cond-> '[cider.nrepl/cider-middleware
                            refactor-nrepl.middleware/wrap-refactor]
                    cljs? (conj 'cemerick.piggieback/wrap-cljs-repl))))
  identity)

;; ========== Testing ==========

(deftask testing []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask test-clj
  "test clj"
  []
  (ensure-deps! [{:boot [:test]}])
  (let [test (r adzerk.boot-test/test)]
    (comp
     (testing)
     (test))))

(deftask test-cljs
  "test cljs"
  []
  (ensure-deps! [{:boot [:cljs-test]}])
  (let [test-cljs (r crisptrutski.boot-cljs-test/test-cljs)]
    (comp
     (testing)
     (test-cljs :exit? true))))

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

(deftask css
  "This is used for creating optimized static resources under static"
  []
  (ensure-deps! [{:boot [:garden
                         :autoprefixer]}])
  (let [garden (r org.martinklepsch.boot-garden/garden)
        autoprefixer (r danielsz.autoprefixer/autoprefixer)]
    (comp
     (garden :pretty-print true)
     (autoprefixer))))

(deftask static
  "This is used for creating optimized static resources under static"
  []
  (ensure-deps! [{:boot [:cljs]}])
  (let [cljs (r adzerk.boot-cljs/cljs)]
    (comp
     (css)
     (cljs :optimizations :advanced))))

(deftask uberjar
  "Build an uberjar"
  []
  (println "Building uberjar")
  (comp
   (aot)
   (pom)
   (uber)
   (jar)
   (target)))

(deftask build-docker
  "Build my application docker zip file."
  [f jar PATH str "path to the jar file"]
  (ensure-deps! [{:boot [:beanstalk]}])
  (let [dockerrun (r adzerk.boot-beanstalk/dockerrun)]
    (comp
     (add-repo)
     (add-file :path jar)
     (dockerrun)
     (zip)
     (target))))

