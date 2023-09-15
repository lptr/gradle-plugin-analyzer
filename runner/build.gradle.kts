import Org_gradlex_plugins_analyzer_plugin_gradle.FindTypeReferencesRunner
import Org_gradlex_plugins_analyzer_plugin_gradle.ShouldNotReferenceInternalApiRunner
import Org_gradlex_plugins_analyzer_plugin_gradle.TypeShouldExtendTypeRunner
import Org_gradlex_plugins_analyzer_plugin_gradle.TypeShouldNotOverrideGetterRunner
import Org_gradlex_plugins_analyzer_plugin_gradle.TypeShouldNotOverrideSetterRunner
import org.gradlex.plugins.analyzer.TypeRepository.TypeSet.ALL_EXTERNAL_REFERENCED_TYPES
import org.gradlex.plugins.analyzer.TypeRepository.TypeSet.EXTERNAL_TASK_TYPES

plugins {
    base
    id("org.gradlex.plugins.analyzer.plugin")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}

pluginAnalyzer {
    analyze(TypeShouldExtendTypeRunner("Task should extend DefaultTask", EXTERNAL_TASK_TYPES, "Lorg/gradle/api/DefaultTask"))
    analyze(TypeShouldNotOverrideGetterRunner(ALL_EXTERNAL_REFERENCED_TYPES))
    analyze(TypeShouldNotOverrideSetterRunner(ALL_EXTERNAL_REFERENCED_TYPES))
    analyze(ShouldNotReferenceInternalApiRunner(ALL_EXTERNAL_REFERENCED_TYPES))
    analyze(FindTypeReferencesRunner(ALL_EXTERNAL_REFERENCED_TYPES, "Lorg/gradle/api/resources/TextResource"))

    plugin("com.android.application")
    plugin("com.android.library")

    // From top community plugins https://docs.google.com/spreadsheets/d/1eNmiNu6VNGQrLApwWPjCi4a2LldIHFGOcQTGj2A4qQM/
    plugin("au.com.dius.pact")
    plugin("com.adarshr.test-logger")
    plugin("com.atlassian.performance.tools.gradle-release")
    plugin("com.avast.gradle.docker-compose")
    plugin("com.bmuschko.docker-java-application")
    plugin("com.bmuschko.docker-remote-api")
    plugin("com.bmuschko.docker-spring-boot-application")
    plugin("com.diffplug.configuration-cache-for-platform-specific-build")
    plugin("com.diffplug.eclipse.excludebuildfolder")
    plugin("com.diffplug.eclipse.mavencentral")
    plugin("com.diffplug.eclipse.projectdeps")
    plugin("com.diffplug.eclipse.resourcefilters")
    plugin("com.diffplug.gradle.eclipse.buildproperties")
    plugin("com.diffplug.gradle.eclipse.projectdeps")
    plugin("com.diffplug.gradle.equinoxlaunch")
    plugin("com.diffplug.gradle.oomph.ide")
    plugin("com.diffplug.gradle.osgi.bndmanifest")
    plugin("com.diffplug.gradle.p2.asmaven")
    plugin("com.diffplug.gradle.spotless")
    plugin("com.diffplug.gradle.swt.nativedeps")
    plugin("com.diffplug.osgi.equinoxlaunch")
    plugin("com.diffplug.p2.asmaven")
    plugin("com.diffplug.spotless")
    plugin("com.diffplug.swt.nativedeps")
    plugin("com.github.ben-manes.versions")
    plugin("com.github.jk1.dependency-license-report")
    // This looks like an old version of "com.github.johnrengelman.shadow", disabled
    // plugin("com.github.johnrengelman.plugin-shadow")
    plugin("com.github.johnrengelman.shadow")
    plugin("com.github.node-gradle.grunt")
    plugin("com.github.node-gradle.gulp")
    plugin("com.github.node-gradle.node")
    plugin("com.github.spotbugs")
    plugin("com.github.spotbugs-base")
    plugin("com.google.cloud.tools.jib")
    plugin("com.google.protobuf")
    plugin("com.gorylenko.gradle-git-properties")
    // Do not analyze the GE plugin as it is full of internal references
    // plugin("com.gradle.build-scan")
    plugin("com.gradle.plugin-publish")
    plugin("com.jfrog.artifactory")
    plugin("com.palantir.docker")
    plugin("com.palantir.docker-compose")
    plugin("com.palantir.docker-run")
    plugin("com.russianprussian.avast.gradle.docker-compose")
    plugin("de.undercouch.download")
    plugin("io.franzbecker.gradle-lombok")
    plugin("io.freefair.lombok")
    plugin("io.gitlab.arturbosch.detekt")
    plugin("io.micronaut.aot")
    plugin("io.micronaut.application")
    plugin("io.micronaut.docker")
    plugin("io.micronaut.graalvm")
    plugin("io.micronaut.library")
    plugin("io.micronaut.minimal.application")
    plugin("io.micronaut.minimal.library")
    plugin("io.qameta.allure")
    plugin("io.spring.dependency-management")
    plugin("nebula.info-basic")
    plugin("net.ltgt.apt")
    plugin("net.ltgt.apt-eclipse")
    plugin("net.ltgt.apt-idea")
    plugin("net.ltgt.errorprone")
    plugin("net.ltgt.errorprone-base")
    plugin("net.ltgt.errorprone-javacplugin")
    plugin("nu.studer.jooq")
    plugin("org.ajoberstar.grgit")
    plugin("org.ajoberstar.grgit-service")
    plugin("org.asciidoctor.convert")
    plugin("org.asciidoctor.jvm.pdf")
    plugin("org.flywaydb.flyway")
    plugin("org.gradle.kotlin.embedded-kotlin")
    plugin("org.gradle.kotlin.kotlin-dsl")
    plugin("org.gradle.kotlin.kotlin-dsl.base")
    plugin("org.gradle.kotlin.kotlin-dsl.compiler-settings")
    plugin("org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins")
    plugin("org.gradle.test-retry")
    plugin("org.jetbrains.gradle.plugin.idea-ext")
    plugin("org.jetbrains.kotlin.android")
    plugin("org.jetbrains.kotlin.android.extensions")
    plugin("org.jetbrains.kotlin.js")
    plugin("org.jetbrains.kotlin.jvm")
    plugin("org.jetbrains.kotlin.kapt")
    plugin("org.jetbrains.kotlin.multiplatform")
    plugin("org.jetbrains.kotlin.multiplatform.pm20")
    plugin("org.jetbrains.kotlin.native.cocoapods")
    plugin("org.jetbrains.kotlin.plugin.allopen")
    plugin("org.jetbrains.kotlin.plugin.jpa")
    plugin("org.jetbrains.kotlin.plugin.noarg")
    plugin("org.jetbrains.kotlin.plugin.parcelize")
    plugin("org.jetbrains.kotlin.plugin.scripting")
    plugin("org.jetbrains.kotlin.plugin.serialization")
    plugin("org.jetbrains.kotlin.plugin.spring")
    plugin("org.jlleitschuh.gradle.ktlint")
    plugin("org.jlleitschuh.gradle.ktlint-idea")
    plugin("org.jmailen.kotlinter")
    plugin("org.liquibase.gradle")
    plugin("org.openapi.generator")
    plugin("org.owasp.dependencycheck")
    plugin("org.sonarqube")
    plugin("org.springframework.boot")

    // From popular plugins – https://docs.google.com/spreadsheets/d/1p-soKHdFdYyrrmokHXg9ug03hK4VoU8oAo7g28Knels/
    plugin("com.google.dagger.hilt.android")
    plugin("com.google.gms.google-services")
    plugin("com.guardsquare.proguard") {
        coordinates = "com.guardsquare:proguard-gradle:7.3.2"
    }
}
