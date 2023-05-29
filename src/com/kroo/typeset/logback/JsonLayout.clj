(ns com.kroo.typeset.logback.JsonLayout
  (:require [jsonista.core :as j])
  (:import (ch.qos.logback.core LayoutBase)
           (ch.qos.logback.classic.spi ILoggingEvent)
           (ch.qos.logback.classic.pattern ThrowableProxyConverter)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair))
  (:gen-class
   :extends LayoutBase
   :init init
   :state state))

;; TODO: configure JSON formatting, e.g. pretty printing.
;; TODO: handle name collisions by stacking @s.
;; TODO: use java.util.Map for performance?
;; TODO: format exception structurally instead of a string.

(defn -init []
  [[] {:ex-conv (ThrowableProxyConverter.)}])

(defn -getContentType ^String [this]
  "application/json")

(defn -doLayout ^String [this ^ILoggingEvent event]
  (j/write-value-as-string
   (into
    {:timestamp (.getInstant event)
     :level     (.toString (.getLevel event))
     :logger    (.getLoggerName event)
     :thread    (.getThreadName event)
     :context   (.getName (.getLoggerContextVO event))
     :message   (.getFormattedMessage event)
     :mdc       (let [^java.util.Map mdc (.getMDCPropertyMap event)]
                  (when-not (.isEmpty mdc) mdc))
     :markers   (let [^java.util.List markers (.getMarkerList event)]
                  (when-not (.isEmpty markers)
                    (mapv #(.getName ^Marker %) markers)))
     :exception (when (.getThrowableProxy event)
                  (when-let [^String throwable
                             (-> this .state :ex-conv (.convert event))]
                    (when-not (.isEmpty throwable)
                      throwable)))}
    (map (fn [^KeyValuePair kv]
           [(.key kv) (.value kv)]))
    (.getKeyValuePairs event))
    ;; TODO: object mapper.
    ,,,))
