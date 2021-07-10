(ns lapis.core
  (:require [clojure.string :as s]
            [duct.core :as core]
            [lapis.db :as db]
            [inflections.core :as inf]
            [integrant.core :as ig]
            [clojure.pprint :as pp]))

(defn handler-key [project-ns action]
  (let [ns (str project-ns ".handler")] (keyword ns action)))

(defn route-key [project-ns resource action]
  (let [ns (str project-ns ".handler." resource)] (keyword ns action)))

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

(defn- col-names [table]
  (set (map :name (:columns table))))

(defmulti root-routes (fn [config & _] (:router config)))

(defmethod root-routes :ataraxy [config table]
  (let [ns (:project-ns config)
        table-name (:name table)
        opts {:db (:db-ref config) :db-keys (:db-keys config)
              :table table-name :cols (col-names table)}
        rsc-path (str "/" (to-path-rsc table-name config) "/")
        rsc-path-end (str "/" (to-path-rsc table-name config))]
    (reduce (fn [m [action path param-names]]
              (let [route-key (route-key ns table-name action)
                    handler-key (handler-key ns action)]
                (-> m
                    (update :routes conj (route-map path route-key param-names))
                    (update :handlers conj (handler-map
                                            handler-key route-key opts)))))
            {:routes [] :handlers []}
            [["list-root" [:get rsc-path-end {'q :query-params}] ['q]]
             ["create-root" [:post rsc-path-end {'b :params}] ['b]]
             ["fetch-root" [:get rsc-path 'id {'q :query-params}] [^int 'id 'q]]
             ["delete-root" [:delete rsc-path 'id] [^int 'id]]
             ["put-root" [:put rsc-path 'id {'b :params}] [^int 'id 'b]]
             ["patch-root" [:patch rsc-path 'id {'b :params}] [^int 'id 'b]]])))

(defmulti one-n-link-routes (fn [config & _] (:router config)))

(defmethod one-n-link-routes :ataraxy [config table table-name p-rsc]
  (let [ns (:project-ns config)
        p-rsc-path (str "/" (to-path-rsc p-rsc config) "/")
        rsc-path (str "/" (to-path-rsc table-name config) "/")
        rsc-path-end (str "/" (to-path-rsc table-name config))
        opts {:db (:db-ref config) :db-keys (:db-keys config)
              :table table-name :p-col (to-col-name p-rsc)
              :cols (col-names table)}
        rscs (str p-rsc "." table-name)]
    (reduce (fn [m [action path param-names]]
              (let [route-key (route-key ns rscs action)
                    handler-key (handler-key ns action)]
                (-> m
                    (update :routes conj (route-map path route-key param-names))
                    (update :handlers conj (handler-map
                                            handler-key route-key opts)))))
            {:routes [] :handlers []}
            [["list-one-n" [:get p-rsc-path 'id rsc-path-end {'q :query-params}]
              [^int 'id 'q]]
             ["create-one-n" [:post p-rsc-path 'id rsc-path-end {'b :params}]
              [^int 'id 'b]]
             ["fetch-one-n" [:get p-rsc-path 'p-id rsc-path 'id
                             {'q :query-params}] [^int 'p-id ^int 'id 'q]]
             ["delete-one-n" [:delete p-rsc-path 'p-id rsc-path 'id]
              [^int 'p-id ^int 'id]]
             ["put-one-n" [:put p-rsc-path 'p-id rsc-path 'id {'b :params}]
              [^int 'p-id ^int 'id 'b]]
             ["patch-one-n" [:patch p-rsc-path 'p-id rsc-path 'id {'b :params}]
              [^int 'p-id ^int 'id 'b]]])))

(defmulti n-n-create-routes (fn [config & _] (:router config)))

(defmethod n-n-create-routes :ataraxy [config table]
  (let [ns (:project-ns config)
        table-name (:name table)
        parts (s/split table-name #"_")
        rsc-a (first (:belongs-to table))
        rsc-b (second (:belongs-to table))
        rsc-a-path (str "/" (to-path-rsc rsc-a config) "/")
        rsc-b-path (str "/" (to-path-rsc rsc-b config) "/")
        opts {:db (:db-ref config) :db-keys (:db-keys config)
              :table table-name :col-a (to-col-name rsc-a)
              :col-b (to-col-name rsc-b) :cols (col-names table)}]
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
              [^int 'id-a ^int 'id-b 'b]]
             ["delete-n-n" (str rsc-a "." rsc-b)
              [:post rsc-a-path 'id-a rsc-b-path 'id-b "/delete"]
              [^int 'id-a ^int 'id-b]]
             ["delete-n-n" (str rsc-b "." rsc-a)
              [:post rsc-b-path 'id-a rsc-a-path 'id-b "/delete"]
              [^int 'id-a ^int 'id-b]]])))


(defmulti n-n-link-routes (fn [config & _] (:router config)))

(defmethod n-n-link-routes :ataraxy [config table table-name p-rsc rsc]
  (let [ns (:project-ns config)
        p-rsc-path (str "/" (to-path-rsc p-rsc config) "/")
        rsc-path (str "/" (to-path-rsc rsc config))
        opts {:db (:db-ref config) :db-keys (:db-keys config)
              :p-col (to-col-name p-rsc) :table table-name
              :cols (col-names table)}]
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

(defn one-n-routes [config table]
  (let [table-name (:name table)]
    (reduce (fn [m p-rsc]
              (let [routes (one-n-link-routes config table table-name p-rsc)]
                (-> m
                    (update :routes concat (:routes routes))
                    (update :handlers concat (:handlers routes)))))
            {:routes [] :handlers []}
            (:belongs-to table))))

(defn n-n-routes [config table]
  (merge-with
   into
   (n-n-create-routes config table)
   (let [table-name (:name table)
         rsc-a (first (:belongs-to table))
         rsc-b (second (:belongs-to table))]
     (reduce (fn [m [p-rsc rsc]]
               (let [routes (n-n-link-routes config table table-name p-rsc rsc)]
                 (-> m
                     (update :routes concat (:routes routes))
                     (update :handlers concat (:handlers routes)))))
             {:routes [] :handlers []}
             [[rsc-a rsc-b] [rsc-b rsc-a]]))))

(defn table-routes [table config]
  (reduce (fn [m relation-type]
            (let [routes (case relation-type
                           :root (root-routes config table)
                           :one-n (one-n-routes config table)
                           :n-n (n-n-routes config table))]
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

(defn n-n-belongs-to [table]
  (let [table-name (:name table)
        parts (s/split table-name #"_" 2)]
    [(first parts) (second parts)]))

(defn links-to [table]
  (reduce (fn [v column]
            (let [name (:name column)]
              (if (is-relation-column? name)
                (conj v (s/replace name "_id" ""))
                v)))
          []
          (:columns table)))

(defn- is-n-n-table? [table]
  (s/includes? (:name table) "_"))

(defn- relation-types [table]
  (if (is-n-n-table? table) [:n-n]
      (if (has-relation-column? table) [:one-n :root] [:root])))

(defn schema-from-db [db]
  (map (fn [table]
         (let [is-n-n (is-n-n-table? table)]
           (cond-> table
             true (assoc :relation-types (relation-types table))
             (not is-n-n) (assoc :belongs-to (links-to table))
             is-n-n (assoc :belongs-to (n-n-belongs-to table)))))
       (db/get-db-schema db)))

(defn- get-project-ns [config options]
  (:project-ns options (:duct.core/project-ns config)))

(defn- get-db
  "Builds database config and returns database map by keys. Required as
  ig/ref to :duct.database/sql is only built in :duct/profile namespace."
  [config db-config-key db-keys]
  (ig/load-namespaces config)
  (let [init-conifg (ig/init config [db-config-key])
        db (second (first (ig/find-derived init-conifg db-config-key)))]
    (get-in db db-keys)))

(defmulti merge-rest-routes (fn [config & _] (:router config)))

(defmethod merge-rest-routes :ataraxy [config duct-config routes]
  (let [flat-routes (apply merge (:routes routes))
        route-config {:duct.router/ataraxy {:routes flat-routes}}
        handler-config (apply merge (:handlers routes))]
    (-> duct-config
        (core/merge-configs route-config)
        (core/merge-configs handler-config))))

(defn make-rest-config [config options]
  (let [db-config-key (:db-config-key options :duct.database/sql)
        db-keys (if (contains? options :db-keys) (:db-keys options) [:spec])
        db-ref (or (:db-ref options) (ig/ref db-config-key))
        db (or (:db options) (get-db config db-config-key db-keys))]
    (println "database: ")
    (println db)
    (-> {}
        (assoc :project-ns (get-project-ns config options))
        (assoc :router (:router options :ataraxy))
        (assoc :db-config-key db-config-key)
        (assoc :db-keys db-keys)
        (assoc :db-ref db-ref)
        (assoc :db db)
        (assoc :tables (or (:tables options) (schema-from-db db)))
        (assoc :table-name-plural (:table-name-plural options true))
        (assoc :resource-path-plural (:resource-path-plural options true)))))

(defmethod ig/init-key ::register [_ options]
  (fn [config]
    (let [rest-config (make-rest-config config options)
          routes (rest-routes rest-config)]
;      (pp/pprint rest-config)
;      (pp/pprint routes)
      (merge-rest-routes rest-config config routes))))