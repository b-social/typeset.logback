(ns com.kroo.typeset.logback.JsonLayout
  (:require [jsonista.core :as j])
  (:import (ch.qos.logback.core LayoutBase)
           (ch.qos.logback.classic.spi ILoggingEvent)
           (ch.qos.logback.classic.pattern ThrowableProxyConverter)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair))
  (:gen-class
   :extends LayoutBase
   :init    init
   :state   state
   :methods [[setPrettyPrint [Boolean] void]
             [setRemoveNullKeyValuePairs [Boolean] void]
             [setTimestampFormat [String] void]
             [setEscapeNonAsciiCharacters [Boolean] void]
             [setAppendLineSeparator [Boolean] void]
             [setIncludeContext [Boolean] void]
             [setIncludeMdc [Boolean] void]
             [setIncludeMarkers [Boolean] void]
             [setIncludeException [Boolean] void]
             [setFormatExceptionAsString [Boolean] void]
             [setIncludeLevelValue [Boolean] void]]))

;; TODO: handle name collisions by stacking @s.
;;  - putIfAbsent
;; TODO: use java.util.Map for performance?
;; TODO: format exception structurally instead of a string.
;; TODO: append newline + option to turn that off

;; Record providing fast access to JsonLayout options.
(defrecord JsonLayoutOpts
  [append-newline
   include-context
   include-mdc
   include-markers
   include-exception
   exception-as-str
   include-level-val
   object-mapper])

(def ^:private ^:const default-opts
  (map->JsonLayoutOpts
   {:pretty            false
    :strip-nils        true
    :date-format       "yyyy-MM-dd'T'HH:mm:ss'Z'"
    :escape-non-ascii  false
    :append-newline    true
    :include-context   false
    :include-mdc       false
    :include-markers   true
    :include-exception true
    :format-ex-as-str  false
    :include-level-val false}))

(defn -init []
  [[] (atom (assoc default-opts
                   :ex-converter  (ThrowableProxyConverter.)
                   :object-mapper (j/object-mapper default-opts)))])

(defn -doLayout ^String [this ^ILoggingEvent event]
  (let [^JsonLayoutOpts opts @(.state this)]
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
     (:object-mapper opts))))

(defn -getContentType ^String [this]
  "application/json")


;;; -------------------------------------
;;; Expose Logback configuration options.

(defmacro ^:private set-opt! [this opt]
  `(swap! (.state ~this) update ~(keyword opt) ~opt))

(defn- update-opt+mapper
  ^JsonLayoutOpts [opts k v]
  (let [opts (assoc opts k v)]
    (assoc opts :object-mapper (j/object-mapper opts))))

(defn -setPrettyPrint [this pretty-print?]
  (swap! (.state this) update-opt+mapper
         :pretty pretty-print?))

(defn -setRemoveNullKeyValuePairs [this remove-nil-kvs?]
  (swap! (.state this) update-opt+mapper
         :strip-nils remove-nil-kvs?))

(defn -setTimestampFormat [this timestamp-format]
  (swap! (.state this) update-opt+mapper
         :date-format timestamp-format))

(defn -setEscapeNonAsciiCharacters [this escape-non-ascii?]
  (swap! (.state this) update-opt+mapper
         :escape-non-ascii escape-non-ascii?))

(defn -setAppendLineSeparator [this append-newline]
  (set-opt! this append-newline))

(defn -setIncludeContext [this include-context]
  (set-opt! this include-context))

(defn -setIncludeMdc [this include-mdc]
  (set-opt! this include-mdc))

(defn -setIncludeMarkers [this include-markers]
  (set-opt! this include-markers))

(defn -setIncludeException [this include-exception]
  (set-opt! this include-exception))

(defn -setFormatExceptionAsString [this format-ex-as-str]
  (set-opt! this format-ex-as-str))

(defn -setIncludeLevelValue [this include-level-val]
  (set-opt! this include-level-val))
