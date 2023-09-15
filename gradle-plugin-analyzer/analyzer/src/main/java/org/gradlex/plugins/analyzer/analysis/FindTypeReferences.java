package org.gradlex.plugins.analyzer.analysis;

import com.google.common.collect.ImmutableSet;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.TypeReference;
import org.gradlex.plugins.analyzer.Analysis;
import org.gradlex.plugins.analyzer.Reference;
import org.gradlex.plugins.analyzer.TypeReferenceWalker;

import java.util.LinkedHashSet;

import static org.gradlex.plugins.analyzer.TypeOrigin.EXTERNAL;
import static org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision.VISIT_AND_CONTINUE;
import static org.slf4j.event.Level.INFO;

public class FindTypeReferences implements Analysis {
    private final ImmutableSet<String> typeNames;

    public FindTypeReferences(String... typeNames) {
        this.typeNames = ImmutableSet.copyOf(typeNames);
    }

    @Override
    public void analyzeType(IClass type, AnalysisContext context) {
        ImmutableSet<TypeReference> types = typeNames.stream()
            .map(context.getResolver()::getReference)
            .collect(ImmutableSet.toImmutableSet());

        var references = new LinkedHashSet<Reference>();
        TypeReferenceWalker.walkReferences(type, context.getResolver(), __ -> VISIT_AND_CONTINUE, references::add);

        references.stream()
            .filter(Reference.sourceIs(EXTERNAL))
            .filter(reference -> reference.target().map(types::contains, __ -> false))
            .map(Reference::format)
            .forEach(message -> context.report(INFO, message));
    }
}
