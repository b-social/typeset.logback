# Typeset.logback

Simple JSON layout component for [Logback][] Classic, with Clojure and [SLF4J][] 2+ key value attribute support.

[Logback]: https://logback.qos.ch/
[SLF4J]: https://www.slf4j.org/


## Installation

> **Note**<br>
> While this library is designed for and written in Clojure, it still works in
> other JVM languages.  To use it, include [`org.clojure/clojure`][clj:mvn] as
> a dependency and add [Clojars](https://clojars.org/) as a Maven repository.

[clj:mvn]: https://central.sonatype.com/artifact/org.clojure/clojure/1.11.1/overview

> **Note**<br>
> Not yet published to Clojars!

```clojure
;; tools.deps
com.kroo/typeset.logback {:mvn/version "0.1"}
;; Leiningen
[com.kroo/typeset.logback "0.1"]
```


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
                <includeContext>false</includeContext>
                <includeLevelValue>false</includeLevelValue>
                <includeMdc>false</includeMdc>
                <includeMarkers>true</includeMarkers>
                <includeException>true</includeException>
            </layout>
        </encoder>
    </appender>

    <logger name="TEMP" level="DEBUG"/>
</configuration>
```


## Legal

Copyright © 2023 Kroo Bank Ltd.

This library and source code are available under the terms of the MIT licence. A full copy of the licence file is provided in the `LICENCE` file of the source code.
