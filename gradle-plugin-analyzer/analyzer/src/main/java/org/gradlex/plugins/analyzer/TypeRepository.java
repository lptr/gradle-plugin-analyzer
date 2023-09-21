package org.gradlex.plugins.analyzer;

import com.google.common.collect.ImmutableList;
import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.FileOfClasses;
import org.gradlex.plugins.analyzer.Reference.MethodTarget;
import org.gradlex.plugins.analyzer.Reference.Target;
import org.gradlex.plugins.analyzer.Reference.TypeTarget;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.gradlex.plugins.analyzer.TypeOrigin.EXTERNAL;
import static org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision.STOP_DONT_VISIT;
import static org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision.VISIT_AND_CONTINUE;

public class TypeRepository {

    private static final String EXCLUSIONS = """
        java\\/awt\\/.*
        javax\\/swing\\/.*
        sun\\/awt\\/.*
        sun\\/swing\\/.*
        com\\/sun\\/.*
        sun\\/.*
        org\\/netbeans\\/.*
        org\\/openide\\/.*
        com\\/ibm\\/crypto\\/.*
        com\\/ibm\\/security\\/.*
        org\\/apache\\/xerces\\/.*
        java\\/security\\/.*
        """;

    private final ClassHierarchy hierarchy;
    private final Map<TypeSet, ImmutableList<IClass>> cache = new HashMap<>();
    private final TypeResolverImpl typeResolver;

    public TypeRepository(Collection<Path> classpath) throws IOException, ClassHierarchyException {
        this.hierarchy = ClassHierarchyFactory.make(createScope(classpath));
        this.typeResolver = new TypeResolverImpl(hierarchy);
    }

    @Nonnull
    private static AnalysisScope createScope(Collection<Path> classpath) throws IOException {
        AnalysisScope scope = AnalysisScopeReader.instance.makePrimordialScope(null);
        classpath.forEach(path -> addToScope(scope, path));
        scope.setExclusions(new FileOfClasses(new ByteArrayInputStream(EXCLUSIONS.getBytes(StandardCharsets.UTF_8))));
        return scope;
    }

    private static void addToScope(AnalysisScope scope, Path path) {
        ClassLoaderReference loader = scope.getLoader(AnalysisScope.APPLICATION);
        if (Files.isRegularFile(path)) {
            try {
                JarFile jar = new JarFile(path.toFile(), false);
                scope.addToScope(loader, jar);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            scope.addToScope(loader, new BinaryDirectoryTreeModule(path.toFile()));
        }
    }

    public TypeResolverImpl getTypeResolver() {
        return typeResolver;
    }

    public ImmutableList<IClass> getTypeSet(TypeSet set) {
        var result = cache.get(set);
        if (result == null) {
            result = set.load(this, new TypeResolverImpl(hierarchy))
                .collect(ImmutableList.toImmutableList());
            cache.put(set, result);
        }
        return result;
    }

    public enum TypeSet {
        TASK_TYPES {
            @Override
            Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver) {
                TypeReference taskType = typeResolver.findReference("Lorg/gradle/api/Task");
                return cache.hierarchy.getImplementors(taskType).stream();
            }
        },
        PLUGIN_TYPES {
            @Override
            Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver) {
                TypeReference pluginType = typeResolver.findReference("Lorg/gradle/api/Plugin");
                return cache.hierarchy.getImplementors(pluginType).stream();
            }
        },
        EXTERNAL_TASK_TYPES {
            @Override
            Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver) {
                return cache.getTypeSet(TASK_TYPES).stream()
                    .filter(TypeOrigin::isExternal);
            }
        },
        EXTERNAL_PLUGIN_TYPES {
            @Override
            Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver) {
                return cache.getTypeSet(PLUGIN_TYPES).stream()
                    .filter(TypeOrigin::isExternal);
            }
        },
        ALL_EXTERNAL_TYPES {
            @Override
            Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver) {
                return Stream.concat(
                    cache.getTypeSet(EXTERNAL_TASK_TYPES).stream(),
                    cache.getTypeSet(EXTERNAL_PLUGIN_TYPES).stream()
                );
            }
        },
        ALL_EXTERNAL_REFERENCED_TYPES {
            @Override
            Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver) {
                ImmutableList<IClass> allExternalTypes = cache.getTypeSet(ALL_EXTERNAL_TYPES);
                var queue = new ArrayDeque<>(allExternalTypes);
                var seenTypes = new HashSet<>(allExternalTypes);

                while (true) {
                    IClass type = queue.poll();
                    if (type == null) {
                        break;
                    }
                    TypeReferenceWalker.walkReferences(
                        type,
                        typeResolver,
                        superType -> TypeOrigin.isExternal(superType)
                            ? VISIT_AND_CONTINUE
                            : STOP_DONT_VISIT,
                        reference -> {
                            Target target = reference.target();
                            switch (target) {
                                case TypeTarget it -> {
                                    IClass targetType = typeResolver.findClass(it.type());
                                    if (targetType != null && TypeOrigin.of(targetType) == EXTERNAL) {
                                        visitTypeIfNecessary(targetType, seenTypes, queue);
                                    }
                                }
                                case MethodTarget it -> {
                                    IMethod targetMethod = it.method();
                                    IClass declaringType = targetMethod.getDeclaringClass();
                                    if (TypeOrigin.of(declaringType) == EXTERNAL) {
                                        visitTypeIfNecessary(declaringType, seenTypes, queue);
                                        visitTypeIfNecessary(targetMethod.getReturnType(), typeResolver, seenTypes, queue);
                                        for (int iParam = 0; iParam < targetMethod.getNumberOfParameters(); iParam++) {
                                            visitTypeIfNecessary(targetMethod.getParameterType(iParam), typeResolver, seenTypes, queue);
                                        }
                                    }
                                }
                            }
                        });
                }
                return seenTypes.stream();
            }

            private static void visitTypeIfNecessary(TypeReference reference, TypeResolver typeResolver, Set<IClass> seenTypes, ArrayDeque<IClass> queue) {
                IClass type = typeResolver.findClass(reference);
                if (type != null) {
                    visitTypeIfNecessary(type, seenTypes, queue);
                }
            }

            private static void visitTypeIfNecessary(IClass type, Set<IClass> seenTypes, ArrayDeque<IClass> queue) {
                if (TypeOrigin.isExternal(type) && seenTypes.add(type)) {
                    queue.add(type);
                }
            }
        };

        abstract Stream<IClass> load(TypeRepository cache, TypeResolver typeResolver);

    }

    @Override
    public String toString() {
        // To avoid printing the whole classpath in Spock error messages
        return "TypeRepository";
    }
}
