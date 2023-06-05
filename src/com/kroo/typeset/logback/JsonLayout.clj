(ns com.kroo.typeset.logback.JsonLayout
  "Simple JSON layout component for Logback Classic, with Clojure and SLF4J 2+
  key value attribute support."
  (:require [jsonista.core :as j])
  (:import (java.util List Map HashMap)
           (ch.qos.logback.core CoreConstants)
           (ch.qos.logback.classic.spi ILoggingEvent ThrowableProxy)
           (ch.qos.logback.classic.pattern ThrowableProxyConverter)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair)
           (com.fasterxml.jackson.databind ObjectMapper))
  (:gen-class
   :extends ch.qos.logback.core.LayoutBase
   :main    false
   :init    init
   :state   state
   :exposes-methods {start superStart, stop superStop}
   :methods [[setPrettyPrint [Boolean] void]
             [setRemoveNullKeyValuePairs [Boolean] void]
             [setTimestampFormat [String] void]
             [setEscapeNonAsciiCharacters [Boolean] void]
             [setAppendLineSeparator [Boolean] void]
             [setIncludeLoggerContext [Boolean] void]
             [setIncludeLevelValue [Boolean] void]
             [setIncludeMdc [Boolean] void]
             [setIncludeMarkers [Boolean] void]
             [setIncludeException [Boolean] void]]))

;; TODO: option to register additional ObjectMapper modules.
;; TODO: option to format exceptions as data.

;; Record providing fast access to JsonLayout options in lieu of fields.
(defrecord JsonLayoutOpts
  [append-newline
   include-logger-ctx
   include-level-val
   include-mdc
   include-markers
   include-exception
   exception-as-str
   object-mapper
   ex-converter])

(defn -init []
  [[] (atom (let [opts (map->JsonLayoutOpts
                        {:pretty             false
                         :strip-nils         true
                         :date-format        "yyyy-MM-dd'T'HH:mm:ss'Z'"
                         :escape-non-ascii   false
                         :append-newline     true
                         :include-logger-ctx false
                         :include-level-val  false
                         :include-mdc        false
                         :include-markers    true
                         :include-exception  true
                         :ex-converter       (ThrowableProxyConverter.)})]
              (assoc opts :object-mapper (j/object-mapper opts))))])

(defn- insert!
  "Inserts a key value pair into a Java map.  If a key with the same name
  already exists, prepends an \"@\" onto the key."
  ^Map [^Map m ^KeyValuePair kv]
  (loop [^String k (.key kv)
         ^Object v (.value kv)]
    (if (.putIfAbsent m k v)
      (recur (str \@ k) v)
      m)))

(defmacro ^:private ->HashMap
  "Less verbose HashMap builder."
  [& kvs]
  `(doto (HashMap.)
     ~@(into ()
             (comp (partition-all 2)
                   (map (fn [x] `(.put ~@x))))
             kvs)))

(defn -doLayout
  ^String [this ^ILoggingEvent event]
  (let [^JsonLayoutOpts opts @(.state this)
        ^Map m (->HashMap "timestamp" (.getInstant event)
                          "level"     (.toString (.getLevel event))
                          "logger"    (.getLoggerName event)
                          "thread"    (.getThreadName event)
                          "message"   (.getFormattedMessage event))]
    (when (:include-logger-ctx opts)
      (.put m "logger_context" (.getName (.getLoggerContextVO event))))
    (when (:include-level-val opts)
      (.put m "level_value" (.toInt (.getLevel event))))
    (when (:include-mdc opts)
      (let [^Map mdc (.getMDCPropertyMap event)]
        (when-not (.isEmpty mdc)
          (.put m "mdc" mdc))))
    (when (:include-markers opts)
      (let [^List markers (.getMarkerList event)]
        (when-not (.isEmpty markers)
          (.put m "markers" (mapv #(.getName ^Marker %) markers)))))
    (when-let [tp (and (:include-exception opts)
                       (.getThrowableProxy event))]
      (when-let [exd (and (instance? ThrowableProxy tp)
                          (ex-data (.getThrowable ^ThrowableProxy tp)))]
        (.put m "ex-data" exd))
      (when-let [ex-str (.convert ^ThrowableProxyConverter (:ex-converter opts) event)]
        (.put m "exception" ex-str)))
    (let [s (j/write-value-as-string
              (reduce insert! m (.getKeyValuePairs event))
              (:object-mapper opts))]
      (if (:append-newline opts)
        (str s CoreConstants/LINE_SEPARATOR)
        s))))

(defn -getContentType ^String [this]
  "application/json")

(defn -start [this]
  (.start ^ThrowableProxyConverter (:ex-converter @(.state this)))
  (.superStart this))

(defn -stop [this]
  (.superStop this)
  (.stop ^ThrowableProxyConverter (:ex-converter @(.state this))))


;;; -------------------------------------
;;; Expose Logback configuration options.

(defmacro ^:private set-opt! [this opt]
  `(swap! (.state ~this) assoc ~(keyword opt) ~opt))

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

(defn -setIncludeLoggerContext [this include-logger-ctx]
  (set-opt! this include-logger-ctx))

(defn -setIncludeLevelValue [this include-level-val]
  (set-opt! this include-level-val))

(defn -setIncludeMdc [this include-mdc]
  (set-opt! this include-mdc))

(defn -setIncludeMarkers [this include-markers]
  (set-opt! this include-markers))

(defn -setIncludeException [this include-exception]
  (set-opt! this include-exception))
