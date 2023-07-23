(ns com.kroo.typeset.logback.JsonLayout-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jsonista.core :as j])
  (:import (com.kroo.typeset.logback JsonLayout)
           (ch.qos.logback.classic.spi ILoggingEvent LoggerContextVO ThrowableProxy)
           (ch.qos.logback.classic Level LoggerContext)
           (org.slf4j.event KeyValuePair)
           (org.slf4j MarkerFactory)
           (java.time Instant)))

(def log-event
  (let [mdc {"hello" "world"}
        kvs [(KeyValuePair. "timestamp" {:foo {:bar 12.4}})
             (KeyValuePair. "things" [1 {:hi {"there" 2}} 3 4])]]
    (reify ILoggingEvent
      (getInstant [_this] (Instant/parse "2023-06-05T22:45:00.0000Z"))
      (getLevel [_this] Level/WARN)
      (getLoggerName [_this] "my.logger")
      (getThreadName [_this] "my.thread")
      (getFormattedMessage [_this] "my formatted message")
      (getMDCPropertyMap [_this] mdc)
      (getKeyValuePairs [_this] kvs)
      (getMarkerList [_this] [(MarkerFactory/getMarker "MyMarker")])
      (getThrowableProxy [_this]
        (ThrowableProxy. (ex-info "Some throwable" {:foo 42})))
      (getLoggerContextVO [_this]
        (LoggerContextVO. (LoggerContext.))))))

(deftest typeset-defaults-test
  (let [typeset (doto (JsonLayout.)
                  (.start))
        expected (j/read-value (io/resource "com/kroo/typeset/logback/defaults.json"))
        actual   (j/read-value (.doLayout typeset log-event))]
    ;; (spit (io/file (io/resource "com/kroo/typeset/logback/defaults.json"))
    ;;       (.doLayout typeset log-event))
    (testing "Returns expected log"
      (is (= (dissoc expected "error.stack")
             (dissoc actual "error.stack"))))
    (testing "Exception line contains the stack trace"
      (is (str/starts-with? (actual "error.stack")
                            "clojure.lang.ExceptionInfo: Some throwable\n\tat com.kroo.typeset.logback.JsonLayout_test")))))

(deftest typeset-options-test
  (let [typeset (doto (JsonLayout.)
                  (.setPrettyPrint true)
                  (.setIncludeLoggerContext true)
                  (.setIncludeLevelValue true)
                  (.setIncludeMdc true)
                  (.setFlattenMdc true)
                  (.setIncludeMarkers false)
                  (.setIncludeException false)
                  (.start))
        expected (j/read-value (io/resource "com/kroo/typeset/logback/options.json"))
        actual   (j/read-value (.doLayout typeset log-event))]
    ;; (spit (io/file (io/resource "com/kroo/typeset/logback/options.json"))
    ;;       (.doLayout typeset log-event))
    (testing "Returns expected log"
      (is (= expected actual)))))

(def bad-log-event-exception
  (ex-info "Hello" {:something "went wrong!"}))

(def bad-log-event
  (let [mdc {"hello" "world"}
        kvs [(KeyValuePair. "timestamp" {:foo {:bar 12.4}})
             (KeyValuePair. "things" [1 {:hi {"there" 2}} 3 4])]]
    (reify ILoggingEvent
      (getInstant [_this] (Instant/parse "2023-06-05T22:45:00.0000Z"))
      (getLevel [_this] Level/WARN)
      (getLoggerName [_this] "my.logger")
      (getThreadName [_this] "my.thread")
      (getFormattedMessage [_this] "my formatted message")
      (getMDCPropertyMap [_this] mdc)
      (getKeyValuePairs [_this] kvs)
      (getMarkerList [_this] (throw bad-log-event-exception))
      (getThrowableProxy [_this]
        (ThrowableProxy. (ex-info "Some throwable" {:foo 42})))
      (getLoggerContextVO [_this]
        (LoggerContextVO. (LoggerContext.))))))

(deftest typeset-error-test
  (let [typeset (doto (JsonLayout.)
                  (.start))
        expected (j/read-value (io/resource "com/kroo/typeset/logback/error.json"))
        actual   (j/read-value (.doLayout typeset bad-log-event))]
    ;; (spit (io/file (io/resource "com/kroo/typeset/logback/error.json"))
    ;;       (.doLayout typeset bad-log-event))
    (testing "Returns failover log"
      (is (= (dissoc expected "error.stack" "timestamp")
             (dissoc actual "error.stack" "timestamp")))
      (testing "Includes timestamp of exception"
        (is (contains? actual "timestamp")))
      (testing "Includes information about the exception"
        (is (contains? actual "error.stack"))
        (is (str/starts-with? (actual "error.stack")
                              "clojure.lang.ExceptionInfo: Hello\n"))))))
