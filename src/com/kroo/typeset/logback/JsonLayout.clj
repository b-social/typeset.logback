(ns com.kroo.typeset.logback.JsonLayout
  "Simple JSON layout component for Logback Classic, with Clojure and SLF4J 2+
  key value attribute support."
  (:require [jsonista.core :as j])
  (:import (java.util List Map HashMap)
           (clojure.lang IPersistentList)
           (ch.qos.logback.core CoreConstants)
           (ch.qos.logback.classic.spi ILoggingEvent)
           (ch.qos.logback.classic.pattern ThrowableProxyConverter)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair)
           (com.fasterxml.jackson.databind ObjectMapper))
  (:gen-class
   :extends ch.qos.logback.core.LayoutBase
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

(set! *warn-on-reflection* true)

;; Record providing fast access to JsonLayout options in lieu of fields.
(defrecord JsonLayoutOpts
  [^Boolean append-newline
   ^Boolean include-context
   ^Boolean include-mdc
   ^Boolean include-markers
   ^Boolean include-exception
   ^Boolean exception-as-str
   ^Boolean include-level-val
   ^ObjectMapper object-mapper
   ^ThrowableProxyConverter ex-converter])

(defn -init []
  [[] (atom (let [opts (map->JsonLayoutOpts
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
                         :include-level-val false
                         :ex-converter      (ThrowableProxyConverter.)})]
              (assoc opts :object-mapper (j/object-mapper opts))))])

(defn- insert!
  "Inserts a key value pair into a Java map.  If a key with the same name
  already exists, prepends an \"@\" onto the key."
  ^Map [^Map m ^IPersistentList kv]
  (let [^String k (first kv)
        ^Object v (second kv)]
    (if (.putIfAbsent m k v)
      (recur m (list (str \@ k) v))
      m)))

(defmacro ^:private ->HashMap
  "Less verbose HashMap builder."
  [& kvs]
  `(doto (HashMap.)
     ~@(transduce
         (comp
           (partition-all 2)
           (map (fn [x] `(.put ~@x))))
         conj
         ()
         kvs)))

(def ^:private KeyValuePair->pair
  "Transducer converting `KeyValuePair` into a list."
  (map (fn [^KeyValuePair kv]
         (list (.key kv) (.value kv)))))

(defn -doLayout
  ^String [this ^ILoggingEvent event]
  (let [^JsonLayoutOpts opts @(.state this)
        ^Map m (->HashMap "timestamp" (.getInstant event)
                          "level"     (.toString (.getLevel event))
                          "logger"    (.getLoggerName event)
                          "thread"    (.getThreadName event)
                          "message"   (.getFormattedMessage event))]
    (when (:include-context opts)
      (.put m "context" (.getName (.getLoggerContextVO event))))
    (when (:include-mdc opts)
      (let [^Map mdc (.getMDCPropertyMap event)]
        (when-not (.isEmpty mdc)
          (.put m "mdc" mdc))))
    (when (:include-markers opts)
      (let [^List markers (.getMarkerList event)]
        (when-not (.isEmpty markers)
          (mapv #(.getName ^Marker m) markers))))
    (when (and (:include-exception opts)
               (.getThrowableProxy event))
      (when-let [^String throwable
                 (.convert ^ThrowableProxyConverter (:ex-converter opts) event)]
        (when-not (.isEmpty throwable)
          throwable)))
    (let [s (j/write-value-as-string
              (transduce KeyValuePair->pair
                         insert!
                         m
                         (.getKeyValuePairs event))
              (:object-mapper opts))]
      (if (:append-newline opts)
        (str s CoreConstants/LINE_SEPARATOR)
        s))))

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

;; TODO: format exeption structurally instead of a string.
;; (defn -setFormatExceptionAsString [this format-ex-as-str]
;;   (set-opt! this format-ex-as-str))

(defn -setIncludeLevelValue [this include-level-val]
  (set-opt! this include-level-val))
