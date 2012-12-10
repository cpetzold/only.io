(defproject jnput "1.0.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [webfui "0.2.1"]]
  :plugins [[lein-cljsbuild "0.2.9"]]
  :cljsbuild { :builds [
   {:source-path "src"
    :compiler
    {:output-to "resources/jnput.js"
     :optimizations :whitespace
     :pretty-print true}}]})