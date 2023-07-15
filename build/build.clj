(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.kroo/typeset.logback)

(def version
  (subs (b/git-process {:git-args ["describe" "--tags" "--abbrev=0"]}) 1))

(def basis (b/create-basis {:project "deps.edn"}))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean
  "Clean the targets folder."
  [_opts]
  (b/delete {:path "target"}))

(defn compile
  [_opts]
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :compile-opts {:direct-linking true}
                  :bindings {#'clojure.core/*assert* false
                             #'clojure.core/*warn-on-reflection* true}}))

(defn jar
  "Build the JAR."
  [_opts]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (compile nil)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     basis
                :src-dirs  ["src"]
                :src-pom "build/pom.xml"})
  (b/jar {:class-dir class-dir
          :jar-file  jar-file}))

(defn install
  "Install the JAR locally."
  [_opts]
  (b/install
    {:basis      basis
     :lib        lib
     :version    version
     :jar-file   jar-file
     :class-dir  class-dir}))

(defn deploy
  "Deploy the JAR to Clojars."
  [_opts]
  (dd/deploy {:installer :remote
              :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path {:lib lib, :class-dir class-dir})}))
