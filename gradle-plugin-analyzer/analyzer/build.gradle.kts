import java.util.stream.Collectors

plugins {
    // Apply the groovy plugin to also add support for Groovy (needed for Spock)
    groovy

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val walaVersion = "1.6.2"
val kotlinVersion = "1.9.20-Beta2"

configurations {
    create("pluginUnderTest")
    create("groovyRuntime")
    create("kotlinRuntime")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    implementation("org.slf4j:slf4j-api:2.0.7")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.7")

    testImplementation("org.codehaus.groovy:groovy:3.0.19") {
        because("We need Java 21 support")
    }

    api("com.ibm.wala:com.ibm.wala.core:${walaVersion}")
    implementation("com.ibm.wala:com.ibm.wala.shrike:${walaVersion}")
    implementation("com.ibm.wala:com.ibm.wala.util:${walaVersion}")
    implementation("com.ibm.wala:com.ibm.wala.cast:${walaVersion}")
    implementation("com.ibm.wala:com.ibm.wala.cast.java:${walaVersion}")
    implementation("com.ibm.wala:com.ibm.wala.cast.java.ecj:${walaVersion}")

    implementation("com.google.guava:guava:32.1.2-jre")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    "pluginUnderTest"("com.adarshr:gradle-test-logger-plugin:3.2.0")

    "groovyRuntime"(localGroovy())
    "kotlinRuntime"("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use Spock test framework
            useSpock("2.2-groovy-3.0")
        }
    }
}

tasks.test {
    systemProperty("plugin-files", project.configurations["pluginUnderTest"].files.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)))
//    systemProperty("plugin-files", tasks.compileJava.get().destinationDirectory.get().asFile.absoluteFile)
    systemProperty("gradle-api", "${gradle.gradleUserHomeDir}/caches/${gradle.gradleVersion}/generated-gradle-jars/gradle-api-${gradle.gradleVersion}.jar")
    systemProperty("local-groovy", configurations["groovyRuntime"].files.map { it.absolutePath }.joinToString(File.pathSeparator))
    systemProperty("local-kotlin", configurations["kotlinRuntime"].files.map { it.absolutePath }.joinToString(File.pathSeparator))
    maxHeapSize = "4096m"
}
