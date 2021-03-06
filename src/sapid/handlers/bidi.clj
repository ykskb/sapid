(ns sapid.handlers.bidi
  (:require [sapid.handlers.core :as c]
            [sapid.qs :as qs]
            [ring.util.response :as ring-res]))

;;; root

(defn list-root [db-con table cols]
  (fn [req]
    (let [query (qs/ring-query req)
          filters (qs/query->filters query cols)]
      (println filters)
      (ring-res/response (c/list-root db-con table filters)))))

(defn create-root [db-con table cols]
  (fn [req]
    (ring-res/response (c/create-root (:params req) db-con table cols))))

(defn fetch-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          query (qs/ring-query req)
          filters (qs/query->filters query cols)]
      (ring-res/response (c/fetch-root id db-con table filters)))))

(defn delete-root [db-con table _cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)]
      (ring-res/response (c/delete-root id db-con table)))))

(defn put-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)]
      (ring-res/response (c/put-root id (:params req) db-con table cols)))))

(defn patch-root [db-con table cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)]
      (ring-res/response (c/patch-root id (:params req) db-con table cols)))))

;;; one-n

(defn list-one-n [db-con table p-col cols]
  (fn [req]
    (let [p-id (-> (:route-params req) :p-id)
          query (qs/ring-query req)
          filters (qs/query->filters query cols)]
      (ring-res/response (c/list-one-n p-col p-id db-con table filters)))))

(defn create-one-n [db-con table p-col cols]
  (fn [req]
    (let [p-id (-> (:route-params req) :p-id)
          params (:params req)]
      (ring-res/response (c/create-one-n p-col p-id params db-con table cols)))))

(defn fetch-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)
          query (qs/ring-query req)
          filters (qs/query->filters query cols)]
      (ring-res/response (c/fetch-one-n id p-col p-id db-con table filters)))))

(defn delete-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)]
      (ring-res/response (c/delete-one-n id p-col p-id db-con table)))))

(defn put-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)
          params (:params req)]
      (ring-res/response (c/put-one-n id p-col p-id params db-con table cols)))))

(defn patch-one-n [db-con table p-col cols]
  (fn [req]
    (let [id (-> (:route-params req) :id)
          p-id (-> (:route-params req) :p-id)
          params (:params req)]
      (ring-res/response (c/patch-one-n id p-col p-id params
                                        db-con table cols)))))

;;; n-n

(defn list-n-n [db-con table nn-table nn-join-col nn-p-col cols]
  (fn [req]
    (let [p-id (-> (:route-params req) :p-id)
          query (qs/ring-query req)
          filters (qs/query->filters query cols)]
      (ring-res/response (c/list-n-n nn-join-col nn-p-col p-id
                                     db-con nn-table table filters)))))

(defn create-n-n [db-con table col-a col-b cols]
  (fn [req]
    (let [id-a (-> (:route-params req) :id-a)
          id-b (-> (:route-params req) :id-b)
          params (:params req)]
      (ring-res/response (c/create-n-n col-a id-a col-b id-b params
                                       db-con table cols)))))

(defn delete-n-n [db-con table col-a col-b _cols]
  (fn [req]
    (let [id-a (-> (:route-params req) :id-a)
          id-b (-> (:route-params req) :id-b)]
      (ring-res/response (c/delete-n-n col-a id-a col-b id-b
                                       db-con table)))))

