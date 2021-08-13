(ns sapid.core
  (:require [clojure.string :as s]
            [duct.core :as core]
            [sapid.db :as db]
            [sapid.route :as rt]
            [sapid.swagger :as sw]
            [inflections.core :as inf]
            [integrant.core :as ig]
            [clojure.pprint :as pp]
            [reitit.swagger :as swagger]))

;;; schema from database

(defn- is-relation-column? [name]
  (s/ends-with? (s/lower-case name) "_id"))

(defn- has-relation-column? [table]
  (some (fn [column] (is-relation-column? (:name column)))
        (:columns table)))

(defn- n-n-belongs-to [table]
  (let [table-name (:name table)
        parts (s/split table-name #"_" 2)]
    [(first parts) (second parts)]))

(defn- links-to [table]
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

(defn- get-ig-db [config db-ig-key db-keys]
  (ig/load-namespaces config)
  (let [init-config (ig/init config [db-ig-key])
        db (or (db-ig-key init-config)
               (second (first (ig/find-derived init-config db-ig-key))))]
    (get-in db db-keys)))

;;; routes per relationship types

(defn- concat-routes [m routes swagger]
  (-> m
      (update :routes concat (:routes routes))
      (update :handlers concat (:handlers routes))
      (update :swag-paths concat (:swag-paths swagger))
      (update :swag-defs concat (:swag-defs swagger))))

(defn- root-routes [config table]
  (let [swagger (sw/root config table)]
    (-> (rt/root-routes config table)
        (assoc :swag-paths (:swag-paths swagger))
        (assoc :swag-defs (:swag-defs swagger)))))

(defn- one-n-routes [config table]
  (reduce (fn [m p-rsc]
            (let [routes (rt/one-n-link-routes config table p-rsc)
                  swagger (sw/one-n config table p-rsc)]
              (concat-routes m routes swagger)))
          {:routes [] :handlers [] :swag-paths [] :swag-defs []}
          (:belongs-to table)))

(defn- n-n-routes [config table]
  (let [create-routes (rt/n-n-create-routes config table)
        create-swagger (sw/n-n-create config table)]
    (merge-with
     into
     (-> create-routes
         (assoc :swag-paths (:swag-paths create-swagger))
         (assoc :swag-defs (:swag-defs create-swagger)))
     (let [table-name (:name table)
           rsc-a (first (:belongs-to table))
           rsc-b (second (:belongs-to table))]
       (reduce (fn [m [p-rsc c-rsc]]
                 (let [link-routes (rt/n-n-link-routes config table p-rsc c-rsc)
                       link-swagger (sw/n-n-link config table p-rsc c-rsc)]
                   (concat-routes m link-routes link-swagger)))
               {:routes [] :handlers [] :swag-paths [] :swag-defs []}
               [[rsc-a rsc-b] [rsc-b rsc-a]])))))

;;; routes 

(defn- table-routes [table config]
  (reduce (fn [m relation-type]
            (let [routes (case relation-type
                           :root (root-routes config table)
                           :one-n (one-n-routes config table)
                           :n-n (n-n-routes config table))]
              (concat-routes m routes routes)))
          {:routes [] :handlers [] :swag-paths [] :swag-defs []}
          (:relation-types table)))

(defn rest-routes
  "Makes routes and handlers from database schema map."
  [config]
  (reduce (fn [m table]
            (let [routes (table-routes table config)]
              (concat-routes m routes routes)))
          {:routes [] :handlers [] :swag-paths [] :swag-defs []}
          (:tables config)))

(defn make-rest-config [options]
  (let [db (:db options)]
    (-> {}
        (assoc :router (:router options))
        (assoc :db db)
        (assoc :tables (or (:tables options) (schema-from-db db)))
        (assoc :table-name-plural (:table-name-plural options true))
        (assoc :resource-path-plural (:resource-path-plural options true))
        (assoc :project-ns (:project-ns options))
        (assoc :db-keys (:db-keys options))
        (assoc :db-ref (:db-ref options)))))

;;; reitit

(defn make-reitit-routes [options]
  (let [db (or (:db options) nil)
        rest-config (make-rest-config (-> options
                                          (assoc :router :reitit)
                                          (assoc :db db)))
        routes (rest-routes rest-config)]
    (rt/add-swag-route rest-config routes)))

(defmethod ig/init-key ::reitit-routes [_ options]
  (make-reitit-routes options))

;;; bidi

(defn make-bidi-routes [options]
  (let [db (or (:db options) nil)
        rest-config (make-rest-config (-> options
                                          (assoc :router :bidi)
                                          (assoc :db db)))
        routes (rest-routes rest-config)]
    (println (apply merge (:routes routes)))
    ["" (apply merge (:routes routes))]))

(defmethod ig/init-key ::bidi-routes [_ options]
  (make-bidi-routes options))

;;; Duct Ataraxy

(defn- get-duct-project-ns [config options]
  (:project-ns options (:duct.core/project-ns config)))

(defn merge-rest-routes [config duct-config routes]
  (let [flat-routes (apply merge (:routes routes))
        route-config {:duct.router/ataraxy {:routes flat-routes}}
        handler-config (apply merge (:handlers routes))]
    (-> duct-config
        (core/merge-configs route-config)
        (core/merge-configs handler-config))))

(defmethod ig/init-key ::duct-routes [_ options]
  (fn [config]
    (let [project-ns (get-duct-project-ns config options)
          db-ig-key (:db-ig-key options :duct.database/sql)
          db-keys (if (contains? options :db-keys) (:db-keys options) [:spec])
          db-ref (or (:db-ref options) (ig/ref db-ig-key))
          db (or (:db options) (get-ig-db config db-ig-key db-keys))
          rest-config (make-rest-config (-> options
                                            (assoc :router :ataraxy)
                                            (assoc :project-ns project-ns)
                                            (assoc :db-keys db-keys)
                                            (assoc :db-ref db-ref)
                                            (assoc :db db)))
          routes (rest-routes rest-config)]
      (merge-rest-routes rest-config config routes))))
