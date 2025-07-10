import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val kotlin_version: String by project
val logback_version: String by project

plugins {
    java // 确保应用了 Java 插件
    kotlin("jvm") version "2.1.20"
    id("io.ktor.plugin") version "3.1.2"
}
val projectVersion = "1.11.3"

group = "com.donut.mixfilecli"
version = projectVersion


application {
    mainClass.set("com.donut.mixfilecli.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")

}



java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}


repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

ktor {
    fatJar {
        archiveFileName.set("mixfile-cli-${version}.jar")
    }
}

dependencies {
    implementation("com.github.InvertGeek:mixfile-core:1.0.3")
    implementation("com.alibaba.fastjson2:fastjson2-kotlin:2.0.56")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.2")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.2")
    implementation("ch.qos.logback:logback-classic:1.3.15")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
