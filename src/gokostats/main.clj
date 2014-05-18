(ns gokostats.main
  (:require [gokostats.server :as server]))

(defn -main [& args]
  (let [port (if args
               (Long/parseLong (first args))
               6789)]
    (def server (server/start-server {:port port}))))
