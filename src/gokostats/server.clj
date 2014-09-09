(ns gokostats.server
  (:require [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.form :as form :refer [form-to]]
            [hiccup.util :refer [escape-html] :rename {escape-html escape}]
            [hiccup.element :refer [link-to]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [clojure.string :as s]
            [clojure.java.jdbc :as jdbc]))

(def db {:subprotocol "postgresql"
         :subname "//gokosalvager.com:5432/goko"
         :user "forum"
         :password "fds"})

(defn in-clause [field values]
  (if (empty? values)
    "true"
    (format "%s in (%s)" field (s/join "," (repeat (count values) "?")))))

(defn winrate-query [{:keys [ignore-short-games ratings]}]
  (format "
  select p2.pname as opponent, count(*) as played,
    sum(case (p.rank - p2.rank)
          when 0 then 50
          when 1 then 0
          when -1 then 100 end
       )/count(*) as win_percent
  from presult p
    join presult p2 using (logfile)
    join game g using (logfile)
  where p.pname=? and p.pcount=2 and p2.pname <> p.pname
    and not g.bot and not g.adventure and %s
    and %s
  group by p2.pname
  having (count(*) > 3)
  order by played desc;
"
          (in-clause "g.rating" ratings)
          (if ignore-short-games
            "p.turns >= 3 and p2.turns >= 3"
            "true")))

(defn input-form
  ([]
     (input-form {:player "", :checked-boxes #{"pro"}}))
  ([{:keys [player checked-boxes]}]
     (letfn [(checkbox
               ([label]
                  (checkbox (s/lower-case label) label))
               ([setting label]
                  (let [field-name (str "include-" setting)]
                    [:span.mode (form/check-box field-name (contains? checked-boxes setting))
                     (form/label field-name label)])))]
       [:div#form
        (form-to [:get "/winrate"]
          [:div
           (form/text-field "player" player)
           (form/submit-button "Get Stats")]
          [:div.modes "Include: "
           (for [rating '[Pro Casual Unrated]]
             (checkbox (name rating)))
           (checkbox "short-games" "Very short games")])])))

(defn salvager-link [path title]
  (link-to (str "http://www.gokosalvager.com" path) title))

(defn header [title]
  [:head
   [:title title]
   (include-css "/static/style.css")])

(def nav-bar
  [:div
   (interpose " | "
              (list (salvager-link "/logsearch" "Log Search")
                    (salvager-link "/kingdomvisualize" "Kingdom Visualizer")
                    (salvager-link "/leaderboard" "Leaderboard")
                    (link-to "http://rspeer.github.io/dominiate/play.html" "Dominiate Simulator")
                    (link-to "https://github.com/aiannacc/Goko-Salvager/wiki" "Goko Salvager Extension")
                    (link-to "https://gokostats.malloys.org/" "Statistics")))])

(defn render-winrates [player-name ratings ignore-short-games?]
  (let [form (input-form {:player player-name
                          :checked-boxes (into (set ratings)
                                               (when-not ignore-short-games?
                                                 ["short-games"]))})]
    (if-let [results (seq
                      (jdbc/query db (list* (winrate-query {:ignore-short-games ignore-short-games?
                                                            :ratings ratings})
                                            player-name ratings)))]
      {:status 200 :headers {"content-type" "text/html; charset=utf-8"}
       :body (html5 (header "Gokostats - Win Rate")
                    [:body
                     nav-bar
                     form
                     [:h2 "Win rates for " (escape player-name)]
                     [:div#stats
                      [:table {:border 0, :cellpadding 2}
                       [:tbody
                        [:tr (for [heading ["Opponent" "Times played" "Win % against"]]
                               [:td.right [:u heading]])]
                        (for [row results]
                          [:tr (for [col [:opponent :played :win_percent]]
                                 [:td.right (escape (get row col))])])]]]])}
      {:status 404 :headers {"content-type" "text/html; charset=utf-8"}
       :body (html5 (header "Gokostats - Not found")
                    [:body
                     nav-bar
                     [:h2 "Player " (escape player-name) " hasn't played 4+ games against anyone"]
                     form])})))

(def form-page
  {:status 200 :headers {"content-type" "text/html; charset=utf-8"}
   :body (html5 (header "Gokostats")
                [:body
                 nav-bar
                 [:h2 "Look up a player's stats"]
                 (input-form)])})

(defn handler []
  (-> (routes (GET "/winrate" {{:keys [player include-short-games] :as params} :params}
                (render-winrates player
                                 (for [rating '[pro casual unrated]
                                       :when (get params (keyword (str "include-" rating)))]
                                   (str rating))
                                 (not include-short-games)))
              (GET "/" []
                form-page)
              (route/resources "/")
              (route/not-found (html5 (header "Not found")
                                      [:body nav-bar "No such page"])))
      (wrap-keyword-params)
      (wrap-params)
      (wrap-stacktrace)))

(defn start-server [opts]
  (let [handler (handler)]
    {:handler handler
     :server (run-jetty handler
                        (merge {:join? false}
                               opts))}))

(defn stop-server [server]
  (.stop (:server server)))
