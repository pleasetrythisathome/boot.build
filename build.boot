(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"})

(require '[pleasetrythisathome.build :refer :all])

(def org "pleasetrythisathome")
(def project "boot.build")
(def version (deduce-version-from-git))

(merge-project-env! (project-env))

(merge-env!
 :dependencies (->> [{:boot [:laces]}]
                    (pull-deps)
                    (scope-as "test")))

(require
 '[adzerk.bootlaces :refer :all]
 '[boot.util :as util])

(bootlaces! version)

(util/info (str (dep) "\n"))

(task-options!
 pom {:project (symbol org project)
      :version version
      :description "Boot built utils"
      :license {"The MIT License (MIT)" "http://opensource.org/licenses/mit-license.php"}
      :url (format "https://github.com/%s/%s" org project)
      :scm {:url (format "https://github.com/%s/%s" org project)}})
