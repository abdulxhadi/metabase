(ns metabase.query-processor-test.date-time-zone-functions-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [java-time :as t]
            [metabase.driver :as driver]
            [metabase.test :as mt]
            [metabase.util.date-2 :as u.date]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                Date extract tests                                              |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn test-temporal-extract
  [{:keys [aggregation breakout expressions fields filter limit]}]
  (if breakout
    (->> (mt/run-mbql-query times {:expressions expressions
                                   :aggregation aggregation
                                   :limit       limit
                                   :filter      filter
                                   :breakout    breakout})
         (mt/formatted-rows [int int]))
    (->> (mt/run-mbql-query times {:expressions expressions
                                   :aggregation aggregation
                                   :limit       limit
                                   :filter      filter
                                   :fields      fields})
         (mt/formatted-rows [int])
         (map first))))


(mt/defdataset times-mixed
  [["times" [{:field-name "index"
              :base-type  :type/Integer}
             {:field-name "dt"
              :base-type  :type/DateTime}
             {:field-name "d"
              :base-type  :type/Date}
             {:field-name        "as_dt"
              :base-type         :type/Text
              :effective-type    :type/DateTime
              :coercion-strategy :Coercion/ISO8601->DateTime}
             {:field-name        "as_d"
              :base-type         :type/Text
              :effective-type    :type/Date
              :coercion-strategy :Coercion/ISO8601->Date}]
    (for [[idx t]
          (map-indexed vector [#t "2004-03-19 09:19:09-00:00[Asia/Ho_Chi_Minh]"
                               #t "2008-06-20 10:20:10-00:00[Asia/Ho_Chi_Minh]"
                               #t "2012-11-21 11:21:11-00:00[Asia/Ho_Chi_Minh]"
                               #t "2012-11-21 11:21:11-00:00[Asia/Ho_Chi_Minh]"])]
      [(inc idx)
       (t/local-date-time t)                                  ;; dt
       (t/local-date t)                                       ;; d
       (t/format "yyyy-MM-dd HH:mm:ss" (t/local-date-time t)) ;; as_dt
       (t/format "yyyy-MM-dd" (t/local-date t))])]])          ;; as_d

(def ^:private temporal-extraction-op->unit
  {:get-second      :second-of-minute
   :get-minute      :minute-of-hour
   :get-hour        :hour-of-day
   :get-day-of-week :day-of-week
   :get-day         :day-of-month
   :get-week        :week-of-year
   :get-month       :month-of-year
   :get-quarter     :quarter-of-year
   :get-year        :year})

(defn- extract
  [x op]
  (u.date/extract x (temporal-extraction-op->unit op)))

(def ^:private extraction-test-cases
  [{:expected-fn (fn [op]          [(extract #t "2004-03-19 09:19:09" op) (extract #t "2008-06-20 10:20:10" op)
                                    (extract #t "2012-11-21 11:21:11" op) (extract #t "2012-11-21 11:21:11" op)])
    :query-fn    (fn [op field-id] {:expressions {"expr" [op [:field field-id nil]]}
                                    :fields      [[:expression "expr"]]})}
   {:expected-fn (fn [op]          (into [] (frequencies [(extract #t "2004-03-19 09:19:09" op)
                                                          (extract #t "2008-06-20 10:20:10" op)
                                                          (extract #t "2012-11-21 11:21:11" op)
                                                          (extract #t "2012-11-21 11:21:11" op)])))
    :query-fn    (fn [op field-id] {:expressions {"expr" [op [:field field-id nil]]}
                                    :aggregation [[:count]]
                                    :breakout    [[:expression "expr"]]})}])

(deftest extraction-function-tests
  (mt/dataset times-mixed
    ;; need to have seperate tests for mongo because it doesn't have supports for casting yet
    (mt/test-drivers (disj (mt/normal-drivers-with-feature :temporal-extract) :mongo)
      (testing "with datetime columns"
        (doseq [[col-type field-id] [[:datetime (mt/id :times :dt)] [:text-as-datetime (mt/id :times :as_dt)]]
                op                  [:get-year :get-quarter :get-month :get-day
                                     :get-day-of-week :get-hour :get-minute :get-second]
                {:keys [expected-fn query-fn]}
                extraction-test-cases]
          (testing (format "extract %s function works as expected on %s column for driver %s" op col-type driver/*driver*)
            (is (= (set (expected-fn op)) (set (test-temporal-extract (query-fn op field-id))))))))

     (testing "with date columns"
       (doseq [[col-type field-id] [[:date (mt/id :times :d)] [:text-as-date (mt/id :times :as_d)]]
               op                  [:get-year :get-quarter :get-month :get-day :get-day-of-week]
               {:keys [expected-fn query-fn]}
               extraction-test-cases]
        (testing (format "extract %s function works as expected on %s column for driver %s" op col-type driver/*driver*)
          (is (= (set (expected-fn op)) (set (test-temporal-extract (query-fn op field-id)))))))))

    (mt/test-driver :mongo
      (testing "with datetimes columns"
        (let [[col-type field-id] [:datetime (mt/id :times :dt)]]
          (doseq [op              [:get-year :get-quarter :get-month :get-day
                                   :get-day-of-week :get-hour :get-minute :get-second]
                  {:keys [expected-fn query-fn]}
                  extraction-test-cases]
           (testing (format "extract %s function works as expected on %s column for driver %s" op col-type driver/*driver*)
             (is (= (set (expected-fn op)) (set (test-temporal-extract (query-fn op field-id)))))))))

      (testing "with date columns"
        (let [[col-type field-id] [:date (mt/id :times :d)]]
          (doseq [op               [:get-year :get-quarter :get-month :get-day :get-day-of-week]
                  {:keys [expected-fn query-fn]}
                  extraction-test-cases]
           (testing (format "extract %s function works as expected on %s column for driver %s" op col-type driver/*driver*)
             (is (= (set (expected-fn op)) (set (test-temporal-extract (query-fn op field-id))))))))))))


(deftest temporal-extraction-with-filter-expresion-tests
  (mt/test-drivers (mt/normal-drivers-with-feature :temporal-extract)
    (mt/dataset times-mixed
      (doseq [{:keys [title expected query]}
              [{:title    "Nested expression"
                :expected [2004]
                :query    {:expressions {"expr" [:abs [:get-year [:field (mt/id :times :dt) nil]]]}
                           :filter      [:= [:field (mt/id :times :index) nil] 1]
                           :fields      [[:expression "expr"]]}}

               {:title     "Nested with arithmetic"
                :expected  [4008]
                :query     {:expressions {"expr" [:* [:get-year [:field (mt/id :times :dt) nil]] 2]}
                            :filter      [:= [:field (mt/id :times :index) nil] 1]
                            :fields      [[:expression "expr"]]}}

               {:title    "Filter using the extracted result - equality"
                :expected [1]
                :query    {:filter [:= [:get-year [:field (mt/id :times :dt) nil]] 2004]
                           :fields [[:field (mt/id :times :index) nil]]}}

               {:title    "Filter using the extracted result - comparable"
                :expected [1]
                :query    {:filter [:< [:get-year [:field (mt/id :times :dt) nil]] 2005]
                           :fields [[:field (mt/id :times :index) nil]]}}

               {:title    "Nested expression in fitler"
                :expected [1]
                :query    {:filter [:= [:* [:get-year [:field (mt/id :times :dt) nil]] 2] 4008]
                           :fields [[:field (mt/id :times :index) nil]]}}]]
        (testing title
          (is (= expected (test-temporal-extract query))))))))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                              Date arithmetics tests                                            |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn date-math
  [op x amount unit col-type]
  (let [amount (if (= op :date-add)
                 amount
                 (- amount))
        fmt    (cond
                 ;; the :date column of :presto should have this format too,
                 ;; but the test data we created for presto is datetime even if we define it as date
                 (and (= driver/*driver* :presto) (#{:text-as-date} col-type))
                 "yyyy-MM-dd"

                 (= unit :millisecond)
                 "yyyy-MM-dd HH:mm:ss.SSS"

                 :else
                 "yyyy-MM-dd HH:mm:ss")]
    (t/format fmt (u.date/add x unit amount))))

(defn- normalize-timestamp-str [x]
  (if (number? x)
    (int x)
    (-> x
        (str/replace  #"T" " ")
        (str/replace  #"Z" ""))))

(defn test-date-math
  [{:keys [aggregation breakout expressions fields filter limit]}]
  (if breakout
    (->> (mt/run-mbql-query times {:expressions expressions
                                   :aggregation aggregation
                                   :limit       limit
                                   :filter      filter
                                   :breakout    breakout})
         (mt/formatted-rows [normalize-timestamp-str normalize-timestamp-str]))
    (->> (mt/run-mbql-query times {:expressions expressions
                                   :aggregation aggregation
                                   :limit       limit
                                   :filter      filter
                                   :fields      fields})
         (mt/formatted-rows [normalize-timestamp-str])
         (map first))))

(deftest date-math-tests
  (mt/dataset times-mixed
    ;; mongo doesn't supports coercion yet so we exclude it here, Tests for it are in [[metabase.driver.mongo.query-processor-test]]
    (mt/test-drivers (disj (mt/normal-drivers-with-feature :date-arithmetics) :mongo)
      (testing "date arithmetic with datetime columns"
        (doseq [[col-type field-id] [[:datetime (mt/id :times :dt)] [:text-as-datetime (mt/id :times :as_dt)]]
                op                  [:date-add :date-subtract]
                unit                [:year :quarter :month :day :hour :minute :second]

                {:keys [expected query]}
                [{:expected [(date-math op #t "2004-03-19 09:19:09" 2 unit col-type) (date-math op #t "2008-06-20 10:20:10" 2 unit col-type)
                             (date-math op #t "2012-11-21 11:21:11" 2 unit col-type) (date-math op #t "2012-11-21 11:21:11" 2 unit col-type)]
                  :query    {:expressions {"expr" [op [:field field-id nil] 2 unit]}
                             :fields      [[:expression "expr"]]}}
                 {:expected (into [] (frequencies
                                       [(date-math op #t "2004-03-19 09:19:09" 2 unit col-type) (date-math op #t "2008-06-20 10:20:10" 2 unit col-type)
                                        (date-math op #t "2012-11-21 11:21:11" 2 unit col-type) (date-math op #t "2012-11-21 11:21:11" 2 unit col-type)]))
                  :query    {:expressions {"expr" [op [:field field-id nil] 2 unit]}
                             :aggregation [[:count]]
                             :breakout    [[:expression "expr"]]}}]]
          (testing (format "%s %s function works as expected on %s column for driver %s" op unit col-type driver/*driver*)
            (is (= (set expected) (set (test-date-math query)))))))

      (testing "date arithmetic with datetime columns"
        (doseq [[col-type field-id] [[:date (mt/id :times :d)] [:text-as-date (mt/id :times :as_d)]]
                op                  [:date-add :date-subtract]
                unit                [:year :quarter :month :day]

                {:keys [expected query]}
                [{:expected [(date-math op #t "2004-03-19 00:00:00" 2 unit col-type) (date-math op #t "2008-06-20 00:00:00" 2 unit col-type)
                             (date-math op #t "2012-11-21 00:00:00" 2 unit col-type) (date-math op #t "2012-11-21 00:00:00" 2 unit col-type)]
                  :query    {:expressions {"expr" [op [:field field-id nil] 2 unit]}
                             :fields      [[:expression "expr"]]}}
                 {:expected (into [] (frequencies
                                       [(date-math op #t "2004-03-19 00:00:00" 2 unit col-type) (date-math op #t "2008-06-20 00:00:00" 2 unit col-type)
                                        (date-math op #t "2012-11-21 00:00:00" 2 unit col-type) (date-math op #t "2012-11-21 00:00:00" 2 unit col-type)]))
                  :query    {:expressions {"expr" [op [:field field-id nil] 2 unit]}
                             :aggregation [[:count]]
                             :breakout    [[:expression "expr"]]}}]]
          (testing (format "%s %s function works as expected on %s column for driver %s" op unit col-type driver/*driver*)
            (is (= (set expected) (set (test-date-math query))))))))))

(deftest date-math-with-extract-test
  (mt/test-drivers (mt/normal-drivers-with-feature :date-arithmetics)
    (mt/dataset times-mixed
      (doseq [{:keys [title expected query]}
              [{:title    "Nested date math then extract"
                :expected [2006 2010 2014]
                :query    {:expressions {"expr" [:get-year [:date-add [:field (mt/id :times :dt) nil] 2 :year]]}
                            :fields [[:expression "expr"]]}}

               {:title   "Nested date math twice"
                :expected ["2006-05-19 09:19:09" "2010-08-20 10:20:10" "2015-01-21 11:21:11"]
                :query    {:expressions {"expr" [:date-add [:date-add [:field (mt/id :times :dt) nil] 2 :year] 2 :month]}
                           :fields [[:expression "expr"]]}}

               {:title    "filter with date math"
                :expected [1]
                :query   {:filter [:= [:get-year [:date-add [:field (mt/id :times :dt) nil] 2 :year]] 2006]
                          :fields [[:field (mt/id :times :index)]]}}]]
        (testing title
          (is (= (set expected) (set (test-date-math query)))))))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                           Convert Timezone tests                                               |
;;; +----------------------------------------------------------------------------------------------------------------+

(mt/defdataset timezones-1
  [["times" [{:field-name "index"
              :base-type  :type/Integer}
             {:field-name "dt"
              :base-type  :type/DateTime}
             {:field-name "dt_ltz"
              :base-type  :type/DateTimeWithLocalTZ}
             {:field-name "dt_tz"
              :base-type  :type/DateTimeWithTZ}
             {:field-name "d"
              :base-type  :type/Date}
             {:field-name        "as_dt"
              :base-type         :type/Text
              :effective-type    :type/DateTime
              :coercion-strategy :Coercion/ISO8601->DateTime}
             {:field-name        "as_d"
              :base-type         :type/Text
              :effective-type    :type/Date
              :coercion-strategy :Coercion/ISO8601->Date}]
    (for [[idx t]
          (map-indexed vector [#t "2004-03-19 09:19:09-00:00[Asia/Ho_Chi_Minh]"
                               #t "2008-06-20 10:20:10-00:00[Asia/Ho_Chi_Minh]"
                               #t "2012-11-21 11:21:11-00:00[Asia/Ho_Chi_Minh]"
                               #t "2012-11-21 11:21:11-00:00[Asia/Ho_Chi_Minh]"])]
      [(inc idx)
       (t/local-date-time t)                                  ;; dt
       (t/with-zone-same-instant t "Asia/Ho_Chi_Minh")        ;; dt_ltz
       (t/with-zone-same-instant t "Asia/Ho_Chi_Minh")        ;; dt_tz
       (t/local-date t)                                       ;; d
       (t/format "yyyy-MM-dd HH:mm:ss" (t/local-date-time t)) ;; as_dt
       (t/format "yyyy-MM-dd" (t/local-date t))])]])          ;; as_d

(defmacro ^:private with-report-timezeone
  [tz & body]
  `(mt/with-temporary-setting-values [:report-timezone ~tz]
     ~@body))

(defn- test-date-convert
  [convert-tz-expression &
   {:keys [aggregation breakout expressions fields filter limit]
    :or   {expressions {"expr" convert-tz-expression}
           filter      [:= [:field (mt/id :times :index) nil] 1]
           fields      [[:expression "expr"]]}}]
  (if breakout
    (->> (mt/run-mbql-query times {:expressions expressions
                                   :aggregation aggregation
                                   :limit       limit
                                   :filter      filter
                                   :breakout    breakout})
         mt/rows
         ffirst)
    (->> (mt/run-mbql-query times {:expressions expressions
                                   :aggregation aggregation
                                   :limit       limit
                                   :filter      filter
                                   :fields      fields})
         mt/rows
         ffirst)))

(def offset->zone
  "A map of all Offset to a zone-id.
  {\"+07\00\" \"Asia/Saigon\"}"
  (into {"+00:00" "UTC"}
        (for [zone-id (java.time.ZoneId/getAvailableZoneIds)]
         [(-> (t/zone-id zone-id)
              .getRules
              (.getOffset (java.time.Instant/now))
              .toString)
          zone-id])))

(deftest convert-timezone-test
  (mt/test-drivers (mt/normal-drivers-with-feature :convert-timezone)
    (mt/dataset timezones-1
      (testing "timestamp with out timezone columns"
        (testing "convert from UTC to +09:00"
          (is (= "2004-03-19T18:19:09+09:00"
                 (test-date-convert [:convert-timezone [:field (mt/id :times :dt) nil]
                                                       (offset->zone "+09:00")
                                                       (offset->zone "UTC")]))))
        (testing "convert to +09:00, from_tz should have default is system-tz (UTC)"
          (is (= "2004-03-19T18:19:09+09:00"
                 (test-date-convert [:convert-timezone [:field (mt/id :times :dt) nil] (offset->zone "+09:00")]))))


       (testing "with report-tz in Europe/Rome(+02:00) "
         (with-report-timezeone "Europe/Rome"
           (testing "from_tz should default to report_tz"
             (is (= "2004-03-19T17:19:09+09:00"
                    (test-date-convert [:convert-timezone [:field (mt/id :times :dt) nil] (offset->zone "+09:00")]))))

          (testing "if from_tz is provided, ignore report_tz"
            (is (= "2004-03-19T18:19:09+09:00"
                   (test-date-convert [:convert-timezone [:field (mt/id :times :dt) nil]
                                       (offset->zone "+09:00")
                                       (offset->zone "+00:00")])))))))

      (testing "timestamp with time zone columns"
        (testing "the result should be returned in the converted timezone"
          (is (= "2004-03-19T11:19:09+09:00"
                 (test-date-convert [:convert-timezone [:field (mt/id :times :dt_tz) nil] (offset->zone "+09:00")]))))

        (testing "report_tz shouldn't affect the result"
          (with-report-timezeone "America/New_York"
            (is (= "2004-03-19T11:19:09+09:00"
                   (test-date-convert [:convert-timezone [:field (mt/id :times :dt_tz) nil] (offset->zone "+09:00")])))))))))