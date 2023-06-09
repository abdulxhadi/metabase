(ns metabase.query-processor.async
  "Mostly legacy namespace that these days is reduced to a single util function, `result-metadata-for-query-async`. TODO
  -- Consider whether there's a place to put this to consolidate things."
  (:require
   [clojure.core.async :as a]
   [metabase.api.common :as api]
   [metabase.query-processor :as qp]
   [metabase.query-processor.context :as qp.context]
   [metabase.query-processor.interface :as qp.i]
   [metabase.query-processor.util :as qp.util]
   [metabase.util :as u]
   [metabase.util.i18n :refer [trs]]
   [metabase.util.log :as log]
   [schema.core :as s])
  (:import
   (clojure.core.async.impl.channels ManyToManyChannel)))

(defn- query-for-result-metadata [query]
  ;; for purposes of calculating the actual Fields & types returned by this query we really only need the first
  ;; row in the results
  (let [query (-> query
                  (assoc-in [:constraints :max-results] 1)
                  (assoc-in [:constraints :max-results-bare-rows] 1)
                  (assoc-in [:info :executed-by] api/*current-user-id*))]
    ;; need add the constraints above before calculating hash because those affect the hash
    ;;
    ;; (normally middleware takes care of calculating query hashes for 'userland' queries but this is not
    ;; technically a userland query -- we don't want to save a QueryExecution -- so we need to add `executed-by`
    ;; and `query-hash` ourselves so the remark gets added)
    (assoc-in query [:info :query-hash] (qp.util/query-hash query))))

(defn- async-result-metadata-reducedf [result context]
  (let [results-metdata (or (get-in result [:data :results_metadata :columns])
                            [])]
    (qp.context/resultf results-metdata context)))

(defn- async-result-metdata-raisef [e context]
  (log/error e (trs "Error running query to determine Card result metadata:"))
  (qp.context/resultf [] context))

(s/defn result-metadata-for-query-async :- ManyToManyChannel
  "Fetch the results metadata for a `query` by running the query and seeing what the QP gives us in return.
   This is obviously a bit wasteful so hopefully we can avoid having to do this. Returns a channel to get the
   results."
  [query]
  (binding [qp.i/*disable-qp-logging* true]
    ;; for MBQL queries we can infer the columns just by preprocessing the query.
    (if-let [inferred-columns (not-empty (u/ignore-exceptions (qp/query->expected-cols query)))]
      (let [chan (a/promise-chan)]
        (a/>!! chan inferred-columns)
        (a/close! chan)
        chan)
      ;; for *native* queries we actually have to run it.
      (let [query (query-for-result-metadata query)]
        (qp/process-query-async query {:reducedf async-result-metadata-reducedf
                                       :raisef   async-result-metdata-raisef})))))
