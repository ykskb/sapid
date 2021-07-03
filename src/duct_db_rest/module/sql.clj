(ns duct-db-rest.module.sql
  (:require [clojure.string :as s]
            [duct.core :as core]
            [duct-db-rest.boundary.db.core :as db]
            [inflections.core :as inf]
            [integrant.core :as ig]
            [clojure.pprint :as pp]))

(defn handler-key [project-ns action]
  (let [ns (str project-ns ".handler.sql")] (keyword ns action)))

(defn route-key [project-ns resource action]
  (let [ns (str project-ns ".handler.sql." resource)] (keyword ns action)))

(defn handler-map [handler-key route-key opts]
  (derive route-key handler-key)
  {[handler-key route-key] opts})

(defn route-map [path route-key param-names]
  (if (coll? param-names)
    {path (into [] (concat [route-key] param-names))}
    {path [route-key]}))

(defn- to-path-rsc [rsc config]
  (if (:resource-path-plural config) (inf/plural rsc) (inf/singular rsc)))

(defn- to-table-name [rsc config]
  (if (:table-name-plural config) (inf/plural rsc) (inf/singular rsc)))

(defn- to-col-name [rsc]
  (str (inf/singular rsc) "_id"))

(defn root-routes [table config]
  (let [ns (:project-ns config)
        table-name (:name table)
        opts {:db (:db-ref config) :table table-name}
        rsc-path (str "/" (to-path-rsc table-name config))]
    (reduce (fn [m [action path param-names]]
              (let [route-key (route-key ns table-name action)
                    handler-key (handler-key ns action)] 
                (-> m
                    (update :routes conj (route-map path route-key param-names))
                    (update :handlers conj (handler-map
                                            handler-key route-key opts)))))
            {:routes [] :handlers []}
            [["list-root" [:get rsc-path {'q :query-params}] ['q]]
             ["create-root" [:post rsc-path {'b :params}] ['b]]])))

(defn one-n-rel-routes [p-rsc table-name config]
  (let [ns (:project-ns config)
        p-rsc-path (str "/" (to-path-rsc p-rsc config) "/")
        rsc-path (str "/" (to-path-rsc table-name config))
        opts {:db (:db-ref config) :table table-name :p-col (to-col-name p-rsc)}
        rscs (str p-rsc "." table-name)]
    (reduce (fn [m [action path param-names]]
              (let [route-key (route-key ns rscs action)
                    handler-key (handler-key ns action)] 
                (-> m
                    (update :routes conj (route-map path route-key param-names))
                    (update :handlers conj (handler-map
                                            handler-key route-key opts)))))
            {:routes [] :handlers []}
            [["list-one-n" [:get p-rsc-path 'id rsc-path {'q :query-params}]
              [^int 'id 'q]]
             ["create-one-n" [:post p-rsc-path 'id rsc-path {'b :params}]
              [^int 'id 'b]]])))

(defn n-n-create-routes [table config]
  (let [ns (:project-ns config)
        table-name (:name table)
        parts (s/split table-name #"_")
        rsc-a (first parts)
        rsc-b (second parts)
        rsc-a-path (str "/" (to-path-rsc rsc-a config) "/")
        rsc-b-path (str "/" (to-path-rsc rsc-b config) "/")
        opts {:db (:db-ref config) :table table-name
              :col-a (to-col-name rsc-a) :col-b (to-col-name rsc-b)}]
    (reduce (fn [m [action rscs path param-names]]
              (let [route-key (route-key ns rscs action)
                    handler-key (handler-key ns action)]
                (-> m
                    (update :routes conj (route-map path route-key param-names))
                    (update :handlers conj (handler-map handler-key
                                                        route-key opts)))))
            {:routes [] :handlers []}
            [["create-n-n" (str rsc-a "." rsc-b)
              [:post rsc-a-path 'id-a rsc-b-path 'id-b "/add" {'b :params}]
              [^int 'id-a ^int 'id-b 'b]]
             ["create-n-n" (str rsc-b "." rsc-a)
              [:post rsc-b-path 'id-a rsc-a-path 'id-b "/add" {'b :params}]
              [^int 'id-a ^int 'id-b 'b]]])))

(defn n-n-list-routes [table-name p-rsc rsc config]
  (let [ns (:project-ns config)
        p-rsc-path (str "/" (to-path-rsc p-rsc config) "/")
        rsc-path (str "/" (to-path-rsc rsc config))
        opts {:db (:db-ref config) :p-col (to-col-name p-rsc)
              :table table-name}]
    (reduce (fn [m [action path param-names]]
              (let [rscs (str p-rsc "." rsc)
                    route-key (route-key ns rscs action)
                    handler-key (handler-key ns action)] 
                (-> m
                    (update :routes conj (route-map path route-key param-names))
                    (update :handlers conj (handler-map
                                            handler-key route-key opts)))))
            {:routes [] :handlers []}
            [["list-one-n" [:get p-rsc-path 'id rsc-path {'q :query-params}]
              [^int 'id 'q]]])))

(defn one-n-routes [table config]
  (let [rsc (:name table)]
    (reduce (fn [m p-rsc]
              (let [routes (one-n-rel-routes p-rsc rsc config)]
                (-> m
                    (update :routes concat (:routes routes))
                    (update :handlers concat (:handlers routes)))))
            {:routes [] :handlers []}
            (:belongs-to table))))

(defn n-n-routes [table config]
  (merge-with
   into
   (n-n-create-routes table config)
   (let [table-name (:name table)
         parts (s/split table-name #"_")
         rsc-a (first parts)
         rsc-b (second parts)]
     (reduce (fn [m [p-rsc rsc]]
               (let [routes (n-n-list-routes table-name p-rsc rsc config)]
                 (-> m
                     (update :routes concat (:routes routes))
                     (update :handlers concat (:handlers routes)))))
             {:routes [] :handlers []}
             [[rsc-a rsc-b] [rsc-b rsc-a]]))))

(defn table-routes [table config]
  (reduce (fn [m relation-type]
            (let [routes (case relation-type
                           :root (root-routes table config)
                           :one-n (one-n-routes table config)
                           :n-n (n-n-routes table config))]
              (-> m
                  (update :routes concat (:routes routes))
                  (update :handlers concat (:handlers routes)))))
          {:routes [] :handlers []}
          (:relation-types table)))

(defn rest-routes
  "Makes routes and handlers from database schema map."
  [config]
  (reduce (fn [m table]
            (let [routes (table-routes table config)]
              (-> m
                  (update :routes concat (:routes routes))
                  (update :handlers concat (:handlers routes)))))
          {:routes [] :handlers []}
          (:tables config)))

(defn is-relation-column? [name]
  (s/ends-with? (s/lower-case name) "_id"))

(defn has-relation-column? [table]
  (some (fn [column] (is-relation-column? (:name column)))
        (:columns table)))

(defn belongs-to [table]
  (reduce (fn [v column]
            (let [name (:name column)]
              (if (is-relation-column? name)
                (conj v (s/replace name "_id" ""))
                v)))
          []
          (:columns table)))

(defn- relation-types [table]
  (if (s/includes? (:name table) "_") [:n-n]
      (if (has-relation-column? table) [:one-n :root] [:root])))

(defn default-schema-map [db]
  (map (fn [table]
         (-> table
             (assoc :relation-types (relation-types table))
             (assoc :belongs-to (belongs-to table))))
       (db/get-db-schema db)))

(defn- get-project-ns [config options]
  (:project-ns options (:duct.core/project-ns config)))

(defn- get-db
  "Builds database config and returns database map by keys. Required as
  ig/ref to :duct.database/sql is only built in :duct/profile namespace."
  [config db-config-key db-key]
  (ig/load-namespaces config)
  (db-key (ig/init config [db-config-key])))

(defn- merge-rest-routes [config rest-config]
  (let [routes (apply merge (:routes rest-config))
        route-config {:duct.router/ataraxy {:routes routes}}
        handler-config (apply merge (:handlers rest-config))]
    (-> config
        (core/merge-configs route-config)
        (core/merge-configs handler-config))))

(defn- make-rest-config [config options]
  (let [db-config-key (:db-config-key options :duct.database/sql)
        db-key (:db-key options :duct.database.sql/hikaricp)
        db-ref (:db-ref options (ig/ref db-config-key))
        db (:db options (get-db config db-config-key db-key))]
    (-> {}
        (assoc :project-ns (get-project-ns config options))
        (assoc :db-config-key db-config-key)
        (assoc :db-key db-key)
        (assoc :db-ref db-ref)
        (assoc :db db)
        (assoc :tables (:tables options (default-schema-map db)))
        (assoc :table-name-plural (:table-name-plural options true))
        (assoc :resource-path-plural (:resource-path-plural options true)))))

(defmethod ig/init-key ::register [_ options]
  (fn [config]
    (let [rest-config (make-rest-config config options)
          routes (rest-routes rest-config)]
      (pp/pprint rest-config)
      (pp/pprint routes)
      (merge-rest-routes config routes))))
