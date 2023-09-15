package org.gradlex.plugins.analyzer.analysis;

import com.ibm.wala.classLoader.IClass;
import org.gradlex.plugins.analyzer.Analysis;
import org.gradlex.plugins.analyzer.Reference;
import org.gradlex.plugins.analyzer.TypeOrigin;
import org.gradlex.plugins.analyzer.TypeReferenceWalker;
import org.gradlex.plugins.analyzer.WalaUtil;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradlex.plugins.analyzer.TypeOrigin.EXTERNAL;
import static org.gradlex.plugins.analyzer.TypeOrigin.INTERNAL;
import static org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision.VISIT_AND_CONTINUE;
import static org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision.VISIT_AND_STOP;
import static org.slf4j.event.Level.WARN;

/**
 * Task implementations should not reference APIs.
 */
public class ShouldNotReferenceInternalApi implements Analysis {
    @Override
    public void analyzeType(IClass type, AnalysisContext context) {
        var references = new LinkedHashSet<Reference>();
        TypeReferenceWalker.walkReferences(
            type,
            context.getResolver(),
            superType -> TypeOrigin.isGradleApi(superType)
                ? VISIT_AND_STOP
                : VISIT_AND_CONTINUE,
            references::add);

        references.stream()
            .filter(Reference.sourceIs(EXTERNAL))
            .filter(Reference.targetIs(INTERNAL)
                .and(ShouldNotReferenceInternalApi::methodHasNoPublicImplementation))
            .map(Reference::format)
            .forEach(message -> context.report(WARN, message));
    }

    private static boolean methodHasNoPublicImplementation(Reference reference) {
        return reference.target().map(
            type -> true,
            method -> {
                IClass type = method.getDeclaringClass();
                // Try to find method in a public supertype
                var hasPublicDefinition = new AtomicBoolean(false);
                WalaUtil.visitTypeHierarchy(type, superType -> {
                    if (TypeOrigin.isPublicGradleApi(superType)) {
                        if (superType.getMethod(method.getSelector()) != null) {
                            hasPublicDefinition.set(true);
                            return false;
                        }
                    }
                    return true;
                });

                // Skip method if it's defined in a public API type
                return !hasPublicDefinition.get();
            }
        );
    }
}
