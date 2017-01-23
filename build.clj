(ns build
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn join-keys
  [ks]
  (->> ks
       (map name)
       (str/join "-")
       keyword))

(defn korks->ks
  [kork]
  (if (keyword? kork)
    (vector kork)
    kork))

(defn flatten-vals
  "takes a hashmap and recursively returns a flattened list of all the values"
  [coll]
  (if ((every-pred coll? sequential?) coll)
    coll
    (mapcat flatten-vals (vals coll))))

(defn select-deps [deps korks]
  (->> korks
       (mapv (comp (partial get-in deps)
                   korks->ks))
       (mapcat flatten-vals)
       (into [])))

(def deps
  '(edn/read-string (slurp "deps.edn")))

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
                                       :reload]}]
                              (select-deps deps)
                              (mapv #(conj % :scope "test"))))))

(require
 '[boot.file :as file]
 '[boot.util :as util]
 '[clojure.java.io :as io]
 '[adzerk.bootlaces :refer :all]
 '[adzerk.boot-cljs :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload :refer [reload]]
 '[adzerk.boot-test :refer [test]]
 '[crisptrutski.boot-cljs-test :as ct]
 '[boot-component.reloaded :refer :all]
 '[environ.core :refer [env]]
 '[org.martinklepsch.boot-garden :refer :all]
 '[pandeiro.boot-http :refer [serve]])

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
  (set-env! :source-paths   #(conj % "test" "dev"))
  (set-env! :resource-paths #(conj % "test" "src"))
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
   (add-file :path "target/borderless.jar")
   (dockerrun)
   (zip)
   (target)))
