(ns com.kroo.typeset.logback.JsonLayout
  "Simple JSON layout component for Logback Classic, with Clojure and SLF4J 2+
   key value attribute support."
  (:require [jsonista.core :as j])
  (:import (ch.qos.logback.classic.pattern ThrowableProxyConverter)
           (ch.qos.logback.classic.spi ILoggingEvent ThrowableProxy)
           (ch.qos.logback.core CoreConstants)
           (java.time Instant)
           (java.util HashMap List Map)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair))
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
             [setIncludeException [Boolean] void]
             [setIncludeExData [Boolean] void]]))

;; Record providing fast access to JsonLayout options in lieu of fields.
(defrecord JsonLayoutOpts
  [append-newline
   include-logger-ctx
   include-level-val
   include-mdc
   include-markers
   include-exception
   include-ex-data
   exception-as-str
   object-mapper
   ex-converter])

(defn -init
  "Method invoked during object initialisation.  Sets the default value for the
  \"state\" field."
  []
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
                         :include-ex-data    true
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

(defn- log-typeset-error
  "In the rare event that Typeset throws an exception while generating the log
   string, try to convert that exception into a log instead."
  [^ILoggingEvent event ^Throwable e]
  (let [msg "com.kroo.typeset.logback.JsonLayout was unable to format log event!"]
    (try
      (str (j/write-value-as-string
            {:timestamp (Instant/now)
             :message   msg
             :level     "ERROR"
             :logger    "com.kroo.typeset.logback.JsonLayout"
             :log-event {:timestamp (.toString (.getInstant event))
                         :logger    (.getLoggerName event)
                         :thread    (.getThreadName event)
                         :message   (.getFormattedMessage event)
                         :level     (.toString (.getLevel event))}
             :exception (Throwable->map e)})
           CoreConstants/LINE_SEPARATOR)
      ;; Another failover for when something is really seriously wrong!
      (catch Throwable _
        (format "{\"timestamp\":\"%s\"\"message\":\"%s\",\"exception\":\"%s\",\"level\":\"ERROR\",\"logger\":\"%s\"}\n"
                (Instant/now)
                msg
                (.toString e)
                "com.kroo.typeset.logback.JsonLayout")))))

(defn -doLayout
  "The core method on a Logback layout component.  This method takes a logging
   event and returns that log formatted as a JSON string."
  ^String [this ^ILoggingEvent event]
  (try
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
        (when-let [^Map mdc (.getMDCPropertyMap event)]
          (when-not (.isEmpty mdc)
            (.put m "mdc" mdc))))
      (when (:include-markers opts)
        (when-let [^List markers (.getMarkerList event)]
          (when-not (.isEmpty markers)
            (.put m "markers" (mapv #(.getName ^Marker %) markers)))))
      (when-let [tp (and (:include-exception opts)
                         (.getThrowableProxy event))]
        (when-let [exd (and (:include-ex-data opts)
                            (instance? ThrowableProxy tp)
                            (ex-data (.getThrowable ^ThrowableProxy tp)))]
          (.put m "ex-data" exd))
        (when-let [ex-str (.convert ^ThrowableProxyConverter (:ex-converter opts) event)]
          (.put m "exception" ex-str)))
      (let [s (j/write-value-as-string
               (reduce insert! m (.getKeyValuePairs event))
               (:object-mapper opts))]
        (if (:append-newline opts)
          (str s CoreConstants/LINE_SEPARATOR)
          s)))
    ;; Failover logging in case we are unable to create the log string!
    (catch Throwable e
      (log-typeset-error event e))))

(defn -getContentType ^String [_this]
  "application/json")

(defn -start [this]
  ;; We have to call `.start` on "ThrowableProxyConverter" to make it
  ;; include stack traces in logs.
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
  "Update an option in the option map and builds a new Jackson ObjectMapper."
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

(defn -setIncludeExData [this include-ex-data]
  (set-opt! this include-ex-data))
