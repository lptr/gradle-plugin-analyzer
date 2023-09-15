package org.gradlex.plugins.analyzer.analysis

import static org.gradlex.plugins.analyzer.TypeRepository.TypeSet.EXTERNAL_TASK_TYPES

class ShouldNotReferenceInternalApiTest extends AbstractAnalysisSpec {
    def "does not signal public API references"() {
        compileJava("""
            class CustomTask extends org.gradle.api.DefaultTask {
                @org.gradle.api.tasks.TaskAction
                public void execute() {
                }
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == []
    }

    def "signals extending internal API"() {
        compileJava("""
            class CustomTask extends org.gradle.jvm.toolchain.internal.task.ShowToolchainsTask {
                @Override
                public boolean hasTaskActions() {
                    return true;
                }
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == [
            "WARN: The method CustomTask.hasTaskActions()Z overrides method org.gradle.api.internal.AbstractTask.hasTaskActions()Z",
            "WARN: The type LCustomTask extends type Lorg/gradle/jvm/toolchain/internal/task/ShowToolchainsTask",
        ]
    }

    def "signals calling internal API"() {
        compileJava("""
            class CustomTask extends org.gradle.api.DefaultTask {
                static {
                    System.out.println(org.gradle.api.internal.TaskOutputsInternal.class.getName());
                }
            
                {
                    getOutputs().getUpToDateSpec();
                }
            
                @org.gradle.api.tasks.TaskAction
                public void execute() {
                    getOutputs().getUpToDateSpec();
                    org.gradle.api.internal.TaskOutputsInternal[] lajos = new org.gradle.api.internal.TaskOutputsInternal[10];
                }
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == [
            "WARN: The method CustomTask.<clinit>()V references type Lorg/gradle/api/internal/TaskOutputsInternal",
            "WARN: The method CustomTask.<init>()V references method org.gradle.api.internal.TaskOutputsInternal.getUpToDateSpec()Lorg/gradle/api/specs/AndSpec;",
            "WARN: The method CustomTask.execute()V references method org.gradle.api.internal.TaskOutputsInternal.getUpToDateSpec()Lorg/gradle/api/specs/AndSpec;",
            "WARN: The method CustomTask.execute()V references type Lorg/gradle/api/internal/TaskOutputsInternal",
        ]
    }

    def "does not signal calling public API"() {
        compileJava("""
            class CustomTask extends org.gradle.api.DefaultTask {
                {
                    // Public API accessed through accidentally leaked internal type:
                    getOutputs().file("output.txt");

                    // Truly internal API:
                    getOutputs().getUpToDateSpec();
                }
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == [
            "WARN: The method CustomTask.<init>()V references method org.gradle.api.internal.TaskOutputsInternal.getUpToDateSpec()Lorg/gradle/api/specs/AndSpec;"
        ]
    }

    def "signals internal API references in method declarations"() {
        compileJava("""
            class CustomTask extends org.gradle.api.DefaultTask {
                CustomTask(org.gradle.api.internal.BuildType buildType) {}
                            
                protected org.gradle.api.internal.TaskOutputsInternal internalApis(org.gradle.api.internal.TaskInputsInternal inputs) throws org.gradle.internal.exceptions.DefaultMultiCauseException {
                    return null;
                } 
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == [
            "WARN: The declaration of method CustomTask.<init>(Lorg/gradle/api/internal/BuildType;)V references type Lorg/gradle/api/internal/BuildType",
            "WARN: The declaration of method CustomTask.internalApis(Lorg/gradle/api/internal/TaskInputsInternal;)Lorg/gradle/api/internal/TaskOutputsInternal; references type Lorg/gradle/api/internal/TaskInputsInternal",
            "WARN: The declaration of method CustomTask.internalApis(Lorg/gradle/api/internal/TaskInputsInternal;)Lorg/gradle/api/internal/TaskOutputsInternal; references type Lorg/gradle/api/internal/TaskOutputsInternal",
            "WARN: The declaration of method CustomTask.internalApis(Lorg/gradle/api/internal/TaskInputsInternal;)Lorg/gradle/api/internal/TaskOutputsInternal; references type Lorg/gradle/internal/exceptions/DefaultMultiCauseException",
        ]
    }

    def "signals internal types in field declarations"() {
        compileJava("""
            class CustomTask extends org.gradle.api.DefaultTask {
                private org.gradle.api.internal.BuildType buildType;
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == [
            "WARN: The field LCustomTask.buildType references type Lorg/gradle/api/internal/BuildType",
        ]
    }

    def "signals use of internal types in annotations"() {
        // org.gradle.internal.instrumentation.api.annotations.InterceptInherited
        compileJava("""
            @org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation({org.gradle.api.internal.BuildType.class})
            class CustomTask extends org.gradle.api.DefaultTask {
                @org.gradle.internal.instrumentation.api.annotations.InterceptInherited
                void doSomething() {}
            }
        """)

        when:
        analyze(EXTERNAL_TASK_TYPES, new ShouldNotReferenceInternalApi())

        then:
        reports == [
            "WARN: The declaration of method CustomTask.doSomething()V references type Lorg/gradle/internal/instrumentation/api/annotations/InterceptInherited",
            "WARN: The type LCustomTask references type Lorg/gradle/api/internal/BuildType",
            "WARN: The type LCustomTask references type Lorg/gradle/internal/instrumentation/api/annotations/VisitForInstrumentation",
        ]
    }
}
