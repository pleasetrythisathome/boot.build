(require '[clojure.java.io :as io])
(require '[clojure.java.shell :as sh])
(require '[clojure.string :as str])

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
                 (mapv (partial get-in deps))
                 (mapcat flatten-vals)
                 (into []))
     scope (mapv #(conj % :scope scope)))))

(set-env!
 :repositories #(conj % ["my.datomic.com" {:url "https://my.datomic.com/repo"
                                           :username (get-sys-env "DATOMIC_USER")
                                           :password (get-sys-env "DATOMIC_PASS")}])
 :dependencies #(vec
                 (concat %
                         (->> [{:boot [:cljs
                                       :cljs-repl
                                       :cljs-test
                                       :laces
                                       :reload
                                       :test
                                       :garden
                                       :http
                                       :beanstalk]}
                               :env
                               :fs]
                              (pull-deps deps "test")))))

(require
 '[boot.file :as file]
 '[boot.util :as util]
 '[clojure.java.io :as io]
 '[adzerk.bootlaces :refer :all]
 '[adzerk.boot-beanstalk :refer [beanstalk dockerrun]]
 '[adzerk.boot-cljs :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload :refer [reload]]
 '[adzerk.boot-test :refer [test]]
 '[crisptrutski.boot-cljs-test :as ct]
 ;;'[boot-component.reloaded :refer :all]
 '[environ.core :refer [env]]
 '[org.martinklepsch.boot-garden :refer :all]
 '[pandeiro.boot-http :refer [serve]]
 '[me.raynes.fs :as fs])

(def version (deduce-version-from-git))
(bootlaces! version)

(task-options!
 pom {:project project
      :version version
      :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}
      :url (str "https://github.com/pleasetrythisathome/" project)
      :scm {:url (str "https://github.com/pleasetrythisathome/" project)}}
 aot {:namespace #{'(symbol (str project ".main"))}}
 jar {:main (symbol (str project ".main"))
      :file (str project ".jar")}
 ct/test-cljs {:namespaces [(symbol (str project ".main-test"))]}
 garden {:output-to "public/css/style.css"
         :vendors ["webkit" "moz" "o"]}
 beanstalk {:name        project
            :version     version
            :description ""
            :access-key  (env :aws-access-key-id)
            :secret-key  (env :aws-secret-key)
            :file        "target/project.zip"
            :stack-name  "64bit Amazon Linux 2016.09 v2.4.0 running Docker 1.12.6"
            :bucket      (str project "-deploy")
            :beanstalk-envs (for [env ["dev" "staging" "prod"]
                                  :let [name (str project "-" env)]]
                              {:name name
                               :cname-prefix name})})

(deftask test-clj
  "test cljs"
  []
  (set-env! :source-paths #(conj % "test"))
  (comp
   (test)))

(deftask test-cljs
  "test cljs"
  []
  (set-env! :source-paths #(conj % "test"))
  (comp
   (ct/prep-cljs-tests)
   (cljs :source-map true)
   (ct/run-cljs-tests)))

(deftask test-all
  "test all"
  []
  (comp
   (test-clj)
   (test-cljs)))

(deftask dev
  "watch and compile css, cljs, init cljs-repl and push changes to browser"
  []
  (set-env! :source-paths
            #(reduce (fn [paths dir]
                       (if (fs/exists? dir)
                         (conj paths dir)
                         paths))
                     % ["test" "dev"]))
  (comp
   (watch)
   (notify)
   (reload :port 3459
           :ip "0.0.0.0"
           :asset-host "/")
   (cljs-repl :port 3458)
   (cljs :source-map true)
   (garden :pretty-print true)
   (target)))

(deftask package
  "compile cljx, garden, cljs, and build a jar"
  []
  (comp
   (cljs :optimizations :advanced)
   (garden)
   (aot)
   (pom)
   (uber)
   (jar)
   (target)))

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

(deftask build-docker
  "Build my application docker zip file."
  []
  (comp
   (add-repo)
   (add-file :path (str "target/" project ".jar"))
   (dockerrun)
   (zip)
   (target)))
