(ns deps
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
  '{:auth          [[buddy "0.13.0"]]
    :automata      [[automat "0.2.0-alpha2"
                     :exclusions [potemkin]]
                    [potemkin "0.4.3"]]
    :apis
    {:keen         [[io.keen/keen-client-api-java "3.0.0"]]
     :stripe       [[org.clojars.populaceio/clj-stripe "1.0.5-SNAPSHOT"]]}
    :aws           [[com.amazonaws/aws-java-sdk "1.9.39"
                     :exclusions [javax.mail/mail
                                  org.apache.httpcomponents/httpclient
                                  commons-logging]]]
    :boot
    {:beanstalk    [[adzerk/boot-beanstalk "0.2.3"]]
     :cljs         [[adzerk/boot-cljs "1.7.228-2"]]
     :cljs-repl    [[com.cemerick/piggieback "0.2.1"
                     :exclusions [org.clojure/clojure
                                  org.clojure/clojurescript]]
                    [adzerk/boot-cljs-repl "0.3.0"]
                    [weasel "0.7.0"]]
     :cljs-test    [[crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT"]]
     :component    [[ib5k/boot-component "0.1.4-SNAPSHOT"]]
     :garden       [[org.martinklepsch/boot-garden "1.3.0-0"]]
     :http         [[pandeiro/boot-http "0.7.3"]]
     :laces        [[adzerk/bootlaces "0.1.13"]]
     :medusa       [[pleasetrythisathome/boot-medusa "0.0.3-SNAPSHOT"
                     :exclusions [org.clojure/tools.namespace]]]
     :reload       [[adzerk/boot-reload "0.4.13"]]
     :template     [[adzerk/boot-template "1.0.0"]]
     :test         [[adzerk/boot-test "1.1.1"]]}
    :clojure       [[org.clojure/clojure "1.9.0-alpha14"]
                    [org.clojure/core.async "0.2.374"]
                    [org.clojure/core.match "0.3.0-alpha4"]
                    [org.clojure/tools.namespace "0.3.0-alpha3"]
                    [org.clojure/tools.nrepl "0.2.12"]
                    [org.clojure/tools.reader "1.0.0-beta1"]]
    :clojurescript [[org.clojure/clojurescript "1.9.293"]
                    [com.google.guava/guava "19.0"]
                    [fence "0.2.0"]]
    :component
    {:aop          [[milesian/aop "0.1.5"]
                    [milesian/identity "0.1.4"]]
     :component    [[com.stuartsierra/component "0.3.1"]
                    [ib5k.holon/component "0.1.2-SNAPSHOT"]]
     :codep        [[pleasetrythisathome.modular/co-dependency "0.2.1-SNAPSHOT"]]
     :schema       [[ib5k/component-schema "0.1.4-SNAPSHOT"]]}
    :config        [[aero "1.0.0-beta2"]]
    :css           [[garden "1.3.2"]
                    [trowel "0.1.0-SNAPSHOT"]]
    :datascript    [[datascript "0.15.5"]]
    :datomic       [[com.datomic/datomic-pro "0.9.5407"
                     :exclusions [joda-time]]
                    [datomic-schema "1.3.0"]
                    [io.rkn/conformity "0.4.0"
                     :exclusions [com.datomic/datomic-free]]]
    :email         [[com.draines/postal "1.11.4"
                     :exclusions [commons-codec]]
                    [commons-codec "1.10"]
                    [pleasetrythisathome/email "0.0.1-SNAPSHOT"]]
    :errors        [[im.chit/hara.event "2.3.4"]
                    [io.aviso/pretty "0.1.26"]]
    :env           [[environ "1.0.2"]]
    :fs            [[me.raynes/fs "1.4.6"]]
    :html
    {:hiccup       [[hiccup "1.0.5"]]}
    :http
    {:aleph        [[aleph "0.4.2-alpha10"]
                    [juxt.modular/aleph "0.1.4"
                     :exclusions [aleph]]]
     :bidi         [[bidi "2.0.9"]
                    [juxt.modular/bidi "0.9.5"]]
     :http-kit     [[http-kit "2.1.21-alpha2"]]
     :ring         [[ring "1.4.0"]
                    [juxt.modular/ring "0.5.3"]]
     :requests     [[clj-http "3.0.1"]
                    [cljs-http "0.1.40"]
                    [org.apache.httpcomponents/httpclient "4.5.2"]]}
    :interpolate   [[bardo "0.1.2-SNAPSHOT"]]
    :json          [[cheshire "5.6.1"]]
    :keyboard      [[spellhouse/phalanges "0.1.6"]]
    :logging
    {:clj          [[com.taoensso/timbre "4.4.0-alpha1"
                     :exclusions [io.aviso/pretty]]]
     :cljs         [[shodan "0.4.2"]]}
    :manifold      [[manifold "0.1.6-alpha4"]]
    :material      [[cljsjs/material "1.1.3-1"]]
    :oauth         [[populaceio/bolt "0.6.0-SNAPSHOT"
                     :exclusions [juxt.modular/co-dependency
                                  ring/ring-core
                                  juxt.modular/email]]]
    :om            [[org.omcljs/om "1.0.0-alpha36"]
                    [sablono "0.7.1"]
                    [cljsjs/react-dom-server "15.0.0-0"]]
    :plumbing      [[prismatic/plumbing "0.5.3"]]
    :schema        [[prismatic/schema "1.1.1"]]
    :specter       [[com.rpl/specter "0.10.0"]]
    :str           [[camel-snake-kebab "0.4.0"]
                    [superstring "2.1.0"]]
    :test
    {:check        [[org.clojure/test.check "0.9.0"]]
     :holon        [[ib5k.holon/test "0.1.0-SNAPSHOT" :exclusions [org.clojure/clojure]]]
     :iota         [[juxt/iota "0.2.3"]]}
    :time
    {:clj          [[clj-time "0.11.0"]]
     :cljs         [[com.andrewmcveigh/cljs-time "0.4.0"]
                    [cljsjs/moment "2.10.6-4"]]}
    :transit       [[com.cognitect/transit-clj "0.8.285"]
                    [com.cognitect/transit-cljs "0.8.237"]
                    [ring-transit-middleware "0.1.2"]]
    :url           [[com.cemerick/url "0.1.1"]]
    :viz           [[rhizome "0.2.5"]]
    :queue         [[factual/durable-queue "0.1.5"
                     :exclusions [byte-streams]]]})

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
                              (build/select-deps deps)
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
