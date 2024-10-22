(ns com.kroo.typeset.logback.JsonLayout
  "Simple JSON layout component for Logback Classic, with Clojure and SLF4J 2+
  key value attribute support."
  (:require [jsonista.core :as j]
            [clojure.string :as str])
  (:import (ch.qos.logback.classic.spi ILoggingEvent IThrowableProxy ThrowableProxy ThrowableProxyUtil)
           (ch.qos.logback.core CoreConstants)
           (clojure.lang Reflector)
           (java.time Instant)
           (java.util HashMap List Map Map$Entry)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair)
           (com.fasterxml.jackson.databind MapperFeature ObjectMapper SerializationFeature))
  (:gen-class
   :extends ch.qos.logback.core.LayoutBase
   :main    false
   :init    init
   :state   state
   :exposes-methods {start superStart, stop superStop}
   :methods [[setPrettyPrint [Boolean] void]
             [setRemoveNullKeyValuePairs [Boolean] void]
             [setRemoveEmptyKeyValuePairs [Boolean] void]
             [setTimestampFormat [String] void]
             [setEscapeNonAsciiCharacters [Boolean] void]
             [setSortKeysLexicographically [Boolean] void]
             [setAppendLineSeparator [Boolean] void]
             [setIncludeLoggerContext [Boolean] void]
             [setIncludeLevelValue [Boolean] void]
             [setIncludeMdc [Boolean] void]
             [setFlattenMdc [Boolean] void]
             [setIncludeMarkers [Boolean] void]
             [setIncludeException [Boolean] void]
             [setIncludeExData [Boolean] void]
             [setJacksonModules [String] void]]))

;; NOTE: since this class is AOT compiled, be careful not to use any anonymous
;; functions or vars that have gensymed names.

;; Record providing fast access to JsonLayout options in lieu of fields.
(defrecord JsonLayoutOpts [append-newline
                           include-logger-ctx
                           include-level-val
                           include-mdc
                           flatten-mdc
                           include-markers
                           include-exception
                           include-ex-data
                           modules
                           object-mapper])

(defn- reify-jackson-module [module]
  (Reflector/invokeConstructor (resolve (symbol module)) (to-array [])))

(defn- reify-jackson-modules [modules]
  (into []
        (comp
         (remove str/blank?)
         (map reify-jackson-module))
        (str/split modules #"[,\s]+")))

(defn- new-object-mapper
  ^ObjectMapper [opts]
  (-> (j/object-mapper opts)
      (.disable SerializationFeature/FAIL_ON_EMPTY_BEANS)
      (.disable SerializationFeature/FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
      (.enable SerializationFeature/WRITE_SELF_REFERENCES_AS_NULL)
      (.enable SerializationFeature/WRITE_ENUMS_USING_TO_STRING)
      (.configure MapperFeature/CAN_OVERRIDE_ACCESS_MODIFIERS false)
      (.configure MapperFeature/OVERRIDE_PUBLIC_ACCESS_MODIFIERS false)
      (.configure MapperFeature/AUTO_DETECT_GETTERS false)
      (.configure MapperFeature/AUTO_DETECT_SETTERS false)
      (.configure MapperFeature/AUTO_DETECT_FIELDS false)))

(defn -init
  "Method invoked during object initialisation.  Sets the default value for the
  \"state\" field."
  []
  [[] (volatile! (let [opts (map->JsonLayoutOpts
                             {:pretty             false
                              :strip-nils         true
                              :strip-empties      false
                              :date-format        "yyyy-MM-dd'T'HH:mm:ss'Z'"
                              :escape-non-ascii   false
                              :order-by-keys      false
                              :append-newline     true
                              :include-logger-ctx false
                              :include-level-val  false
                              :include-mdc        true
                              :flatten-mdc        true
                              :include-markers    true
                              :include-ex-data    true
                              :include-exception  true
                              :modules            []})]
                   (assoc opts :object-mapper (new-object-mapper opts))))])

(defn- insert-kvp!
  "Inserts an SLF4J `KeyValuePair` into a Java map.  If a key with the same
  name already exists, prepends \"@\"-symbols onto the key until it is unique."
  ^Map [^Map m ^KeyValuePair kv]
  (loop [^String k (.key kv)
         ^Object v (.value kv)]
    (if (.putIfAbsent m k v)
      (recur (str \@ k) v)
      m)))

(defn- insert-me!
  "Inserts a map entry into a Java map.  If a key with the same name already
  exists, prepends \"@\"-symbols onto the key until it is unique."
  ^Map [^Map m ^Map$Entry me]
  (loop [^String k (.getKey me)
         ^Object v (.getValue me)]
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

(defn- extract-ex-data
  "Given a throwable, returns a vector of all the ex-data values within that
  throwable chain.  If the given throwable doesn't have a cause, returns the
  ex-data for just that throwable (or `nil`)."
  [^Throwable t]
  (if (.getCause t)
    (let [ex-datas (loop [acc (transient []), ^Throwable t t]
                     (if t
                       (recur (conj! acc (ex-data t)) (.getCause t))
                       (persistent! acc)))]
      (when-not (every? nil? ex-datas)
        ex-datas))
    (ex-data t)))

(defn- log-typeset-error
  "In the rare event that Typeset throws an exception while generating the log
  string, try to convert that exception into a log instead."
  [^ILoggingEvent event ^Throwable e]
  (let [msg "com.kroo.typeset.logback.JsonLayout was unable to format log event!"]
    (try
      (str (j/write-value-as-string
            {"timestamp"     (Instant/now)
             "message"       msg
             "level"         "ERROR"
             "logger.name"   "com.kroo.typeset.logback.JsonLayout"
             "log_event"     {"timestamp"          (.toString (.getInstant event))
                              "logger.name"        (.getLoggerName event)
                              "logger.thread_name" (.getThreadName event)
                              "message"            (.getFormattedMessage event)
                              "level"              (.toString (.getLevel event))}
             "error.message" (.getMessage e)
             "error.kind"    (.getClass e)
             "error.stack"   (ThrowableProxyUtil/asString (ThrowableProxy. e))})
           CoreConstants/LINE_SEPARATOR)
      ;; A second failover for when something is *very* seriously wrong!
      (catch Throwable _
        (format "{\"timestamp\":\"%s\"\"message\":%s,\"exception\":%s,\"level\":\"ERROR\",\"logger.name\":\"%s\"}\n"
                (Instant/now)
                (pr-str msg)
                (pr-str (or (str e) "null"))
                "com.kroo.typeset.logback.JsonLayout")))))

(defn -doLayout
  "The core method on a Logback layout component.  This method takes a logging
  event and returns that log formatted as a JSON string."
  ^String [this ^ILoggingEvent event]
  (try
    (let [^JsonLayoutOpts opts @(.state this)
          ^Map m (->HashMap "timestamp"          (.getInstant event)
                            "level"              (.toString (.getLevel event))
                            "logger.name"        (.getLoggerName event)
                            "logger.thread_name" (.getThreadName event)
                            "message"            (.getFormattedMessage event))]
      (when (:include-logger-ctx opts)
        (.put m "logger.context_name" (.getName (.getLoggerContextVO event))))
      (when (:include-level-val opts)
        (.put m "level_value" (.toInt (.getLevel event))))
      (when (:include-mdc opts)
        (when-let [^Map mdc (.getMDCPropertyMap event)]
          (when-not (.isEmpty mdc)
            (if (:flatten-mdc opts)
              (reduce insert-me! m mdc)
              (.put m "mdc" mdc)))))
      (when (:include-markers opts)
        (when-let [^List markers (.getMarkerList event)]
          (when-not (.isEmpty markers)
            (.put m "markers" (mapv #(.getName ^Marker %) markers)))))
      (when-let [^IThrowableProxy tp (and (:include-exception opts)
                                          (.getThrowableProxy event))]
        (when-let [exd (and (:include-ex-data opts)
                            (not (.isCyclic tp))
                            (instance? ThrowableProxy tp)
                            (extract-ex-data (.getThrowable ^ThrowableProxy tp)))]
          (.put m "error.data" exd))
        (.put m "error.kind" (.getClassName tp))
        (.put m "error.message" (.getMessage tp))
        (.put m "error.stack" (ThrowableProxyUtil/asString tp)))
      (let [s (j/write-value-as-string
               (reduce insert-kvp! m (.getKeyValuePairs event))
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
  (.superStart this))

(defn -stop [this]
  (.superStop this))

;;; -------------------------------------
;;; Expose Logback configuration options.

(defn- update-opt+mapper
  "Update an option in the option map and builds a new Jackson ObjectMapper."
  ^JsonLayoutOpts [opts k v]
  (let [opts (assoc opts k v)]
    (assoc opts :object-mapper (new-object-mapper opts))))

(defmacro ^:private defopt-json [method-name key]
  `(defn ~method-name [this# value#]
     (vswap! (.state this#) update-opt+mapper ~key value#)))

(defmacro ^:private defopt [method-name key]
  `(defn ~method-name [this# value#]
     (vswap! (.state this#) assoc ~key value#)))

(defopt-json -setPrettyPrint :pretty)
(defopt-json -setRemoveNullKeyValuePairs :strip-nils)
(defopt-json -setRemoveEmptyKeyValuePairs :strip-empties)
(defopt-json -setTimestampFormat :date-format)
(defopt-json -setEscapeNonAsciiCharacters :escape-non-ascii)
(defopt-json -setSortKeysLexicographically :order-by-keys)

(defn -setJacksonModules [this modules]
  (vswap! (.state this) update-opt+mapper
          :modules (reify-jackson-modules modules)))

(defopt -setAppendLineSeparator :append-newline)
(defopt -setIncludeLoggerContext :include-logger-ctx)
(defopt -setIncludeLevelValue :include-level-val)
(defopt -setIncludeMdc :include-mdc)
(defopt -setFlattenMdc :flatten-mdc)
(defopt -setIncludeMarkers :include-markers)
(defopt -setIncludeException :include-exception)
(defopt -setIncludeExData :include-ex-data)
