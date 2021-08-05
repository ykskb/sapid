(ns sapid.handler
  (:require [ataraxy.response :as atr-res]
            [clojure.string :as s]
            [clojure.walk :as w]
            [sapid.db :as db]
            [integrant.core :as ig]
            [ring.middleware.params :as prm]
            [ring.util.response :as ring-res]))

(def ^:private operator-map
  {"eq"  :=
   "lt"  :<
   "le"  :<=
   "lte" :<=
   "gt"  :>
   "ge"  :>=
   "gte" :>=
   "ne"  :!=})

(defn- parse-filter-val [k v]
  (let [parts (s/split v #":" 2)
        op (get operator-map (first parts))]
    (if (or (nil? op) (< (count parts) 2))
      [:= (keyword k) v]
      [op (keyword k) (second parts)])))

(defn- query->filters [query cols]
  (reduce (fn [vec [k v]]
            (if (contains? cols k)
              (conj vec (parse-filter-val k v))
              vec))
          []
          query))

(defn ring-query [req]
  (:query-params (prm/params-request req)))

;;; core root

(defn list-root [query db-con table cols]
  (db/list-up db-con table (query->filters query cols)))

(defn create-root [params db-con table cols]
  (db/create! db-con table (select-keys params cols))
  nil)

(defn fetch-root [id query db-con table cols]
  (db/fetch db-con table id (query->filters query cols)))

(defn delete-root [id db-con table]
  (db/delete! db-con table id)
  nil)

(defn put-root [id params db-con table cols]
  (db/update! db-con table id (select-keys params cols))
  nil)

(defn patch-root [id params db-con table cols]
  (db/update! db-con table id (select-keys params cols))
  nil)

;; bidi root

(defn bidi-list-root [db-con table cols]
  (fn [req]
    (let [query (ring-query req)]
      (ring-res/response (list-root query db-con table cols)))))

(defn bidi-create-root [db-con table cols]
  (fn [req]
    (ring-res/response (create-root (:params req) db-con table cols))))

(defn bidi-fetch-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          query (ring-query req)]
      (ring-res/response (fetch-root id query db-con table cols)))))

(defn bidi-delete-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)]
      (ring-res/response (delete-root id db-con table)))))

(defn bidi-put-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)]
      (ring-res/response (put-root id (:params req) db-con table cols)))))

(defn bidi-patch-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)]
      (ring-res/response (patch-root id (:params req) db-con table cols)))))

;; duct ataraxy root

(defmethod ig/init-key ::list-root [_ {:keys [db db-keys table cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ query] :ataraxy/result}]
      [::atr-res/ok (list-root query db-con table cols)])))

(defmethod ig/init-key ::create-root [_ {:keys [db db-keys table cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ params] :ataraxy/result}]
      [::atr-res/ok (create-root (w/stringify-keys params)
                                 db-con table cols)])))

(defmethod ig/init-key ::fetch-root [_ {:keys [db db-keys table cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ id {:as query}] :ataraxy/result}]
      [::atr-res/ok (fetch-root id query db-con table cols)])))

(defmethod ig/init-key ::delete-root [_ {:keys [db db-keys table cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ id] :ataraxy/result}]
      [::atr-res/ok (delete-root id db-con table)])))

(defmethod ig/init-key ::put-root [_ {:keys [db db-keys table cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ id {:as params}] :ataraxy/result}]
      ;; TODO: params to be cover all the attributes as PUT spec
      [::atr-res/ok (put-root id (w/stringify-keys params)
                              db-con table cols)])))

(defmethod ig/init-key ::patch-root [_ {:keys [db db-keys table cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ id {:as params}] :ataraxy/result}]
      [::atr-res/ok (patch-root id (w/stringify-keys params)
                                db-con table cols)])))

;;; core one-n

(defn list-one-n [p-col p-id query db-con table cols]
  (let [filters (query->filters (assoc query p-col p-id) cols)]
    (db/list-up db-con table filters)))

(defn create-one-n [p-col p-id params db-con table cols]
  (let [params (-> (assoc params p-col p-id)
                   (select-keys cols))]
    (db/create! db-con table params)
    nil))

(defn fetch-one-n [id p-col p-id query db-con table cols]
  (let [filters (query->filters (assoc query p-col p-id) cols)]
    (db/fetch db-con table id filters)))

(defn delete-one-n [id p-col p-id db-con table]
  (db/delete! db-con table id p-col p-id)
  nil)

(defn put-one-n [id p-col p-id params db-con table cols]
  (db/update! db-con table id (select-keys params cols) p-col p-id)
  nil)

(defn patch-one-n [id p-col p-id params db-con table cols]
  (db/update! db-con table id (select-keys params cols) p-col p-id)
  nil)

;; bidi one-n

(defn bidi-list-one-n [db-con table p-col cols]
  (fn [req]
    (let [p-id (-> (:route-params req) :p-id)
          query (ring-query req)]
      (ring-res/response (list-one-n p-col p-id query db-con table cols)))))

(defn bidi-create-one-n [db-con table p-col cols]
  (fn [req]
    (let [p-id (-> (:route-params req) :p-id)
          params (:params req)]
      (ring-res/response (create-one-n p-col p-id params db-con table cols)))))

(defn bidi-fetch-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)
          query (ring-query req)]
      (ring-res/response (fetch-one-n id p-col p-id query db-con table cols)))))

(defn bidi-delete-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)]
      (ring-res/response (delete-one-n id p-col p-id db-con table)))))

(defn bidi-put-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)
          params (:params req)]
      (ring-res/response (put-one-n id p-col p-id params db-con table cols)))))

(defn bidi-patch-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)
          params (:params req)]
      (ring-res/response (patch-one-n id p-col p-id params
                                      db-con table cols)))))

;; duct ataraxy one-n

(defmethod ig/init-key ::list-one-n [_ {:keys [db db-keys table p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id {:as query}] :ataraxy/result}]
      [::atr-res/ok (list-one-n p-col p-id query db-con table cols)])))

(defmethod ig/init-key ::create-one-n [_ {:keys [db db-keys table p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id {:as params}] :ataraxy/result}]
      [::atr-res/ok (create-one-n p-col p-id (w/stringify-keys params)
                                  db-con table cols)])))

(defmethod ig/init-key ::fetch-one-n [_ {:keys [db db-keys table p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id id {:as query}] :ataraxy/result}]
      [::atr-res/ok (fetch-one-n id p-col p-id query db-con table cols)])))

(defmethod ig/init-key ::delete-one-n [_ {:keys [db db-keys table p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id id] :ataraxy/result}]
      [::atr-res/ok (delete-one-n id p-col p-id db-con table)])))

(defmethod ig/init-key ::put-one-n [_ {:keys [db db-keys table p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id id {:as params}] :ataraxy/result}]
      ;; TODO: params to be cover all the attributes for PUT 
      [::atr-res/ok (put-one-n id p-col p-id (w/stringify-keys params)
                               db-con table cols)])))
 
(defmethod ig/init-key ::patch-one-n [_ {:keys [db db-keys table p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id id {:as params}] :ataraxy/result}]
      [::atr-res/ok (patch-one-n id p-col p-id (w/stringify-keys params)
                                 db-con table cols)])))

;;; core n-n

(defn list-n-n [nn-join-col nn-p-col p-id query db-con nn-table table cols]
  (let [nn-link-col (str "nn." nn-p-col)
        cols (conj cols nn-link-col)
        filters (query->filters (assoc query nn-link-col p-id) cols)]
    (db/list-through db-con table nn-table nn-join-col filters)))

(defn create-n-n [col-a id-a col-b id-b params db-con table cols]
  (let [params (-> params (assoc col-a id-a) (assoc col-b id-b))]
    (db/create! db-con table params)
    nil))

(defn delete-n-n [col-a id-a col-b id-b db-con table cols]
  (let [filters (query->filters {col-a id-a col-b id-b} cols)]
    (db/delete-where! db-con table filters)
    nil))

;;; bidi n-n

(defn bidi-list-n-n [db-con table nn-table nn-join-col nn-p-col cols]
  (fn [req]
    (let [p-id (-> (:route-params req) :p-id)
          query (ring-query req)]
      (ring-res/response (list-n-n nn-join-col nn-p-col p-id query
                                   db-con nn-table table cols)))))

(defn bidi-create-n-n [db-con table col-a col-b cols]
  (fn [req]
    (let [id-a (-> (:route-params req) :id-a)
          id-b (-> (:route-params req) :id-b)
          params (:params req)]
      (ring-res/response (create-n-n col-a id-a col-b id-b params
                                     db-con table cols)))))

(defn bidi-delete-n-n [db-con table col-a col-b cols]
  (fn [req]
    (let [id-a (-> (:route-params req) :id-a)
          id-b (-> (:route-params req) :id-b)]
      (ring-res/response (delete-n-n col-a id-a col-b id-b db-con
                                     table cols)))))

;;; duct ataraxy n-n

(defmethod ig/init-key ::list-n-n
  [_ {:keys [db db-keys table nn-table nn-join-col nn-p-col cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ p-id {:as query}] :ataraxy/result}]
      [::atr-res/ok (list-n-n nn-join-col nn-p-col p-id
                              query db-con nn-table table cols)])))

(defmethod ig/init-key ::create-n-n
  [_ {:keys [db db-keys table col-a col-b cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ id-a id-b {:as params}] :ataraxy/result}]
      [::atr-res/ok (create-n-n col-a id-a col-b id-b (w/stringify-keys params)
                                db-con table cols)])))

(defmethod ig/init-key ::delete-n-n
  [_ {:keys [db db-keys table col-a col-b cols]}]
  (let [db-con (get-in db db-keys)]
    (fn [{[_ id-a id-b] :ataraxy/result}]
        [::atr-res/ok (delete-n-n col-a id-a col-b id-b db-con table cols)])))

(defmethod ig/init-key ::static [_ {:keys [db]}]
  (fn [{[c] :ataraxy/result}]
    (let [res (db/get-db-schema db)]
      [::atr-res/ok res])))
