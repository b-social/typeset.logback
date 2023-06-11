(defproject com.kroo/typeset.logback "0.1"
  :url "https://github.com/b-social/typeset.logback"
  :description "Simple JSON layout and formatter components for Logback Classic; with SLF4J 2+ key value attribute support."
  :license {:name "MIT"
            :url "https://github.com/b-social/typeset.logback/blob/master/LICENCE"
            :distribution :repo}
  :pedantic? true
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :aot [com.kroo.typeset.logback.JsonLayout]
  :global-vars {*warn-on-reflection* true
                *assert*             false}
  :dependencies [[org.clojure/clojure "1.11.1" :scope "provided"]
                 [ch.qos.logback/logback-classic "1.4.7" :scope "provided"]
                 [metosin/jsonista "0.3.7"]]
  :plugins [[com.github.clj-kondo/lein-clj-kondo "RELEASE"]
            [com.github.liquidz/antq "RELEASE"]
            [dev.weavejester/lein-cljfmt "RELEASE"]]
  :aliases {"lint" ["clj-kondo" "--lint" "src" "test"]
            "fmt"  ["cljfmt" "fix"]
            "antq" ["with-profile" "+dev" "antq"]})
