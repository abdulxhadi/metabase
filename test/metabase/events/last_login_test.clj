(ns metabase.events.last-login-test
  (:require
   [clojure.test :refer :all]
   [metabase.events.last-login :as last-login]
   [metabase.models.user :refer [User]]
   [metabase.test :as mt]
   [toucan2.core :as t2]))

(deftest user-login-test
  (testing "`:user-login` event"
    (mt/with-temp User [{user-id :id, last-login :last_login}]
      (is (= nil
             last-login))
      (last-login/process-last-login-event {:topic :user-login
                                            :item  {:user_id    user-id
                                                    :session_id "doesntmatter"}})
      (is (some? (t2/select-one-fn :last_login User :id user-id))))))
