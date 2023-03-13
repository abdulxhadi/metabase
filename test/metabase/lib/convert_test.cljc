(ns metabase.lib.convert-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [metabase.lib.convert :as lib.convert]
   [metabase.lib.test-metadata :as meta]
   #?@(:cljs ([metabase.test-runner.assert-exprs.approximately-equal]))))

(deftest ^:parallel ->pMBQL-test
  (is (=? {:lib/type :mbql/query
           :type     :pipeline
           :stages   [{:lib/type     :mbql.stage/mbql
                       :lib/options  {:lib/uuid uuid?}
                       :source-table 1}
                      {:lib/type    :mbql.stage/mbql
                       :lib/options {:lib/uuid uuid?}
                       :fields      [[:field {:lib/uuid uuid?} 2]
                                     [:field {:lib/uuid uuid?, :temporal-unit :month} 3]]
                       :aggregation [[:count {:lib/uuid uuid?}]]}]
           :database 1}
          (lib.convert/->pMBQL
           {:database 1
            :type     :query
            :query    {:source-query {:source-table 1}
                       :fields       [[:field 2 nil]
                                      [:field 3 {:temporal-unit :month}]]
                       :aggregation  [[:count]]}})))
  (testing ":field clause"
    (are [clause expected] (=? expected
                               (lib.convert/->pMBQL clause))
      [:field 2 nil]                     [:field {:lib/uuid uuid?} 2]
      [:field 3 {:temporal-unit :month}] [:field {:lib/uuid uuid?, :temporal-unit :month} 3])))

(deftest ^:parallel ->pMBQL-idempotency-test
  (is (=? {:lib/type :mbql/query
           :type     :pipeline
           :stages   [{:lib/type     :mbql.stage/mbql
                       :lib/options  {:lib/uuid uuid?}
                       :source-table 1}
                      {:lib/type    :mbql.stage/mbql
                       :lib/options {:lib/uuid uuid?}
                       :fields      [[:field {:lib/uuid uuid?} 2]
                                     [:field {:lib/uuid uuid?, :temporal-unit :month} 3]]
                       :aggregation [[:count {:lib/uuid uuid?}]]}]
           :database 1}
          (lib.convert/->pMBQL
           (lib.convert/->pMBQL
            {:database 1
             :type     :query
             :query    {:source-query {:source-table 1}
                        :fields       [[:field 2 nil]
                                       [:field 3 {:temporal-unit :month}]]
                        :aggregation  [[:count]]}}))))
  (testing ":field clause"
    (are [clause expected] (=? expected
                               (lib.convert/->pMBQL (lib.convert/->pMBQL clause)))
      [:field 2 nil]                     [:field {:lib/uuid uuid?} 2]
      [:field 3 {:temporal-unit :month}] [:field {:lib/uuid uuid?, :temporal-unit :month} 3])))

(deftest ^:parallel ->pMBQL-joins-test
  (is (=? {:lib/type :mbql/query
           :database (meta/id)
           :type     :pipeline
           :stages   [{:lib/type    :mbql.stage/mbql
                       :lib/options {:lib/uuid uuid?}
                       :fields      [[:field
                                      {:lib/uuid uuid?, :join-alias "CATEGORIES__via__CATEGORY_ID"}
                                      (meta/id :categories :name)]]
                       :joins       [{:lib/type    :mbql/join
                                      :lib/options {:lib/uuid uuid?}
                                      :alias       "CATEGORIES__via__CATEGORY_ID"
                                      :condition   [:=
                                                    {:lib/uuid uuid?}
                                                    [:field
                                                     {:lib/uuid uuid?}
                                                     (meta/id :venues :category-id)]
                                                    [:field
                                                     {:lib/uuid uuid?, :join-alias "CATEGORIES__via__CATEGORY_ID"}
                                                     (meta/id :categories :id)]]
                                      :strategy    :left-join
                                      :fk-field-id (meta/id :venues :category-id)
                                      :stages      [{:lib/type     :mbql.stage/mbql
                                                     :lib/options  {:lib/uuid uuid?}
                                                     :source-table (meta/id :venues)}]}]}]}
          (lib.convert/->pMBQL
           {:database (meta/id)
            :type     :query
            :query    {:fields [[:field (meta/id :categories :name) {:join-alias "CATEGORIES__via__CATEGORY_ID"}]]
                       :joins  [{:alias        "CATEGORIES__via__CATEGORY_ID"
                                 :source-table (meta/id :venues)
                                 :condition    [:=
                                                [:field (meta/id :venues :category-id)]
                                                [:field (meta/id :categories :id) {:join-alias "CATEGORIES__via__CATEGORY_ID"}]]
                                 :strategy     :left-join
                                 :fk-field-id  (meta/id :venues :category-id)}]}}))))