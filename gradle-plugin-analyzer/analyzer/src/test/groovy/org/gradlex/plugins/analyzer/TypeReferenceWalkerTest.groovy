package org.gradlex.plugins.analyzer

import org.gradlex.plugins.analyzer.analysis.AbstractAnalysisSpec

import static org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision.VISIT_AND_CONTINUE

class TypeReferenceWalkerTest extends AbstractAnalysisSpec {
    def "annotation references are properly tracked"() {
        compileJava """
            import java.lang.annotation.*;

            @Retention(RetentionPolicy.CLASS)
            @interface ClassRef {
                Class<?> value();
            }
            
            @Retention(RetentionPolicy.CLASS)
            @interface StringRef {
                String value();
            }

            @ClassRef(org.gradle.api.internal.TaskOutputsInternal.class)
            @StringRef("org.gradle.api.internal.TaskInputsInternal")
            interface CustomType {
            }
        """

        def repository = new TypeRepository(files)
        def types = new TreeSet<String>()
        def methods = new TreeSet<String>()

        def clazz = repository.typeResolver.findClass("LCustomType")
        TypeReferenceWalker.walkReferences(clazz, repository.typeResolver, __ -> VISIT_AND_CONTINUE, reference -> reference.target().map(
            type -> types += type.name.toString(),
            method -> methods += method.signature
        ))

        expect:
        types.toList() == [
            "LClassRef",
            "LStringRef",
            "Ljava/lang/Object",
            "Lorg/gradle/api/internal/TaskOutputsInternal",
        ]
        methods.toList() == []
    }
}
