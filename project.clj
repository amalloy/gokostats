(defproject gokostats "0.1.0-SNAPSHOT"
  :description "A webpage for displaying some Goko stats for Dominion players"
  :url "malloys.org:6789/winrate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [ring "1.1.0"]
                 [compojure "1.1.6"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [postgresql/postgresql "8.4-702.jdbc4"]
                 [org.flatland/useful "0.11.1"]]
  :main gokostats.main
  :jvm-opts ["-Xmx40m"])
