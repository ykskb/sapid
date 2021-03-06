(defproject sapid "0.1.0-SNAPSHOT"
  :description "Automatic GraphQL / REST API with RDBMS"
  :url "https://github.com/ykskb/sapid"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.github.seancorfield/honeysql "2.0.0-rc3"]
                 [org.xerial/sqlite-jdbc "3.34.0"]
                 [inflections "0.13.2"]]
  :plugins [[duct/lein-duct "0.12.3"]]
  :main ^:skip-aot sapid.main
  :resource-paths ["resources" "target/resources"]
  :prep-tasks     ["javac" "compile" ["run" ":duct/compiler"]]
  :middleware     [lein-duct.plugin/middleware]
  :profiles
  {:dev  [:project/dev :profiles/dev]
   :repl {:prep-tasks   ^:replace ["javac" "compile"]
          :repl-options {:init-ns user}}
   :uberjar {:aot :all}
   :profiles/dev {}
   :project/dev  {:source-paths   ["dev/src"]
                  :resource-paths ["dev/resources"]
                  :dependencies   [[bidi "2.1.6"]
                                   [integrant/repl "0.3.2"]
                                   [hawk "0.2.11"]
                                   [eftest "0.5.9"]
                                   [kerodon "0.9.1"]
                                   [camel-snake-kebab "0.4.2"]
                                   [com.github.seancorfield/next.jdbc "1.2.674"]
                                   [com.walmartlabs/lacinia "0.39-alpha-9"]
                                   [duct/core "0.8.0"]
                                   [duct/module.ataraxy "0.3.0"]
                                   [duct/module.logging "0.5.0"]
                                   [duct/module.sql "0.6.1"]
                                   [duct/module.web "0.7.3"]
                                   [metosin/reitit "0.5.15"]
                                   [threatgrid/ring-graphql-ui "0.1.3"]
                                   [ring/ring-core "1.9.3"]
                                   [ring/ring-json "0.5.1"]
                                   [ring/ring-jetty-adapter "1.9.3"]]}})
