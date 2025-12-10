# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

- **Build Application**: `./gradlew build`
- **Run Application**: `./gradlew bootRun`
- **Run Tests**: `./gradlew test`
- **Run Single Test**: `./gradlew test --tests "com.yt.server.TestClassName"`
- **Build Native Image**: `./gradlew nativeCompile`
- **Clean**: `./gradlew clean`

## Architecture Overview

This is a **Spring Boot 3** application configured for **GraalVM Native Image** compilation (`spring-native`).

### Core Components
- **Web Layer**: Standard Spring MVC Controllers (`src/main/java/com/yt/server/controller`).
- **Data Access**: **MyBatis** is used for ORM. Mappers are located in `src/main/java/com/yt/server/mapper` and corresponding XMLs in `src/main/resources/com/yt/server/mapper`.
- **Database**: Connects to MySQL via **HikariCP**.
- **AOT Support**: Extensive Ahead-Of-Time (AOT) configuration (in `com.yt.server.aot`) to support native image generation, including runtime hints and reflection configuration.
- **Data Processing**: Contains custom logic for handling large files (`BigFileReader`) and data downsampling (`QueryDownsamplingRowHandler`).
- **Sharding**: Custom sharding logic implementation located in `com.yt.server.service.shard`.

### Key Configuration
- **Application Properties**: Primary configuration in `src/main/resources/application.properties`. `application.yml` appears to be legacy/commented out.
- **Server Port**: Defaults to `17777`.
- **Logging**: Logback configured to write to `./logs/spring.log`.

### Build System
- **Tool**: Gradle.
- **Java Version**: JDK 17.
- **Dependencies**: Spring Boot Starter Web, MyBatis, Netty, Hutool, FastJSON.
- @src\main\java\com\yt\server\service\IoComposeServiceDatabase.java 这个文件是我的主要实现逻辑