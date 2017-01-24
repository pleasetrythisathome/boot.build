(require '[boot.file :as file]
         '[boot.util :as util]
         '[clojure.java.io :as io]
         '[clojure.java.shell :as sh]
         '[clojure.string :as str])

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
                         (->> [{:boot [:laces]}]
                              (pull-deps deps "test")))))

(deftask show-version
  "Show version"
  []
  (println (deduce-version-from-git)))

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

(deftask nrepl
  "start a nrepl server"
  []
  (comp
   (repl :server true)
   (watch)))
