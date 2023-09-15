package org.gradlex.plugins.analyzer.analysis

import static org.gradlex.plugins.analyzer.TypeRepository.TypeSet.EXTERNAL_TASK_TYPES

class FindTypeReferencesTest extends AbstractAnalysisSpec {
    def "finds types referenced from external API"() {
        compileJava("""
            class CustomTask extends org.gradle.api.DefaultTask {
                @org.gradle.api.tasks.TaskAction
                public void execute() {
                }
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new FindTypeReferences(
            "Lorg/gradle/api/Task",
            "Lorg/gradle/api/DefaultTask",
        ))

        then:
        reports == [
            "INFO: The type LCustomTask extends type Lorg/gradle/api/DefaultTask",
            "INFO: The type LCustomTask extends type Lorg/gradle/api/Task",
        ]
    }
}
