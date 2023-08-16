package org.gradlex.plugins.analyzer.analysis

class TaskImplementationDoesNotOverrideSetterTest extends AbstractAnalysisSpec {
    def "can detect type overriding setter"() {
        classLoader.parseClass("""
            class CustomTask extends org.gradle.api.DefaultTask {
                @Override
                void setEnabled(boolean enabled) {
                    System.out.println("Overriding")
                }
            }
        """)

        when:
        analyzer.analyze(new TaskImplementationDoesNotOverrideSetter())

        then:
        reports == [
            "WARN: Setter setEnabled() in LCustomTask overrides Gradle API from Lorg/gradle/api/DefaultTask"
        ]
    }
}