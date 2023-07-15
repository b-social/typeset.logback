# Typeset.logback

Simple JSON layout component for [Logback][] Classic, with Clojure and [SLF4J][] 2+ key value attribute support.

[Logback]: https://logback.qos.ch/
[SLF4J]: https://www.slf4j.org/


## Installation

[![Clojars Project](https://img.shields.io/clojars/v/com.kroo/typeset.logback.svg)](https://clojars.org/com.kroo/typeset.logback)

```clojure
;; tools.deps
com.kroo/typeset.logback {:mvn/version "0.2"}
ch.qos.logback/logback-classic {:mvn/version "1.4.8"}

;; Leiningen
[com.kroo/typeset.logback "0.2"]
[ch.qos.logback/logback-classic "1.4.8"]
```

> **Note**<br>
> Make sure to include `ch.qos.logback/logback-classic` as a dependency in your
> project.

> **Note**<br>
> While this library is designed for and written in Clojure, it still works in
> other JVM languages.  To use it, include [`org.clojure/clojure`][clj:mvn] as
> a dependency and add [Clojars](https://clojars.org/) as a Maven repository.

[clj:mvn]: https://central.sonatype.com/artifact/org.clojure/clojure/1.11.1/overview


## Usage

Use Typeset.logback with the default options:

```xml
<!-- logback.xml -->
<configuration scan="false" scanPeriod="5 seconds">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="com.kroo.typeset.logback.JsonLayout" />
        </encoder>
    </appender>

    <logger name="TEMP" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

Use Typeset.logback and configure the various options:

```xml
<!-- logback.xml -->
<configuration scan="false" scanPeriod="5 seconds">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="com.kroo.typeset.logback.JsonLayout">
                <!-- The following are the default values. -->
                <prettyPrint>false</prettyPrint>
                <removeNullKeyValuePairs>true</removeNullKeyValuePairs>
                <timestampFormat>yyyy-MM-dd'T'HH:mm:ss.SSS'Z'</timestampFormat>
                <escapeNonAsciiCharacters>false</escapeNonAsciiCharacters>
                <appendLineSeparator>true</appendLineSeparator>
                <includeLoggerContext>false</includeLoggerContext>
                <includeLevelValue>false</includeLevelValue>
                <includeMdc>false</includeMdc>
                <flattenMdc>false</flattenMdc>
                <includeMarkers>true</includeMarkers>
                <includeException>true</includeException>
                <includeExData>true</includeExData>
            </layout>
        </encoder>
    </appender>

    <logger name="TEMP" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

Example log output (with pretty printing enabled):

```jsonc
{
  "timestamp" : "2023-06-02T14:43:55.685557Z",
  "level" : "WARN",
  "level_value" : 30000,         // <includeLevelValue>true</includeLevelValue>
  "logger.name" : "my.logger",
  "logger.context_name" : "default",  // <includeLoggerContext>true</includeLoggerContext>
  "logger.thread_name" : "my.thread",
  "message" : "My formatted message",
  "markers" : [ "my-marker" ],
  "mdc" : {                      // <includeMdc>true</includeMdc>
    "some-id" : "24676689-cffa-461b-964e-d3fedafe31b5"
  },
  // SLF4J key value pairs.
  "things" : [ 1, {
    "hi" : {
      "there" : 2
    }
  }, 3, 4 ],
  "@timestamp" : {               // Duplicate keys get "@" prepended to them.
    "foo" : {
      "bar" : 12.4
    }
  },
}
```

If you need a structured logging frontend for Clojure, [Epilogue](https://github.com/b-social/epilogue) pairs perfectly with Typeset.logback.


## Troubleshooting

If for some reason you are not getting any logs or are missing some, either your configuration is incorrect or an exception is likely being thrown somewhere.  To get some troubleshooting info, you can edit your `logback.xml` file to include `debug="true"` (or add the `-Dlogback.debug=true` JVM option).

```xml
<!-- logback.xml -->
<configuration debug="true" ...>
  ...
</configuration>
```

If an exception occurs while Typeset.logback is formatting a log event, an "ERROR" log will be appended to the Logback appender that contains details of the exception.


## Legal

Copyright © 2023 Kroo Bank Ltd.

This library and source code are available under the terms of the MIT licence. A full copy of the licence file is provided in the `LICENCE` file of the source code.
