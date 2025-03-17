
val kotlin_version: String by project
val logback_version: String by project

plugins {
    java // 确保应用了 Java 插件
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.1"
}

group = "con.donut.mixfilecli"
version = "1.5.0"

application {
    mainClass.set("com.donut.mixfilecli.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


java {
    sourceCompatibility = JavaVersion.VERSION_17// 设置源代码兼容性为 Java 21
    targetCompatibility = JavaVersion.VERSION_17 // 设置字节码兼容性为 Java 21

    // 可选：配置 Gradle 使用的 JVM 版本
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Gradle 将自动下载或使用 Java 21
    }
}

repositories {
    mavenCentral()
}

ktor {
    fatJar {
        archiveFileName.set("mixfile-cli-${version}.jar")
    }
}

dependencies {
    val ktor_version = "3.1.1"
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-gson:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.2")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.2")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-serialization-gson-jvm")
    implementation("io.ktor:ktor-server-default-headers-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-cio-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("com.github.amr:mimetypes:0.0.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
