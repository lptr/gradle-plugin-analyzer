package org.gradlex.plugins.analyzer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum TypeOrigin {
    PUBLIC(true),
    INTERNAL(true),
    RUNTIME(false),
    EXTERNAL(false);

    private static final List<Pattern> PUBLIC_PACKAGES = Stream.of(
            "org/gradle/*",
            "org/gradle/api/**",
            "org/gradle/authentication/**",
            "org/gradle/build/**",
            "org/gradle/buildinit/**",
            "org/gradle/caching/**",
            "org/gradle/concurrent/**",
            "org/gradle/deployment/**",
            "org/gradle/env/**",
            "org/gradle/external/javadoc/**",
            "org/gradle/ide/**",
            "org/gradle/includedbuild/**",
            "org/gradle/ivy/**",
            "org/gradle/jvm/**",
            "org/gradle/language/**",
            "org/gradle/maven/**",
            "org/gradle/nativeplatform/**",
            "org/gradle/normalization/**",
            "org/gradle/platform/**",
            "org/gradle/play/**",
            "org/gradle/plugin/devel/**",
            "org/gradle/plugin/repository/*",
            "org/gradle/plugin/use/*",
            "org/gradle/plugin/management/*",
            "org/gradle/plugins/**",
            "org/gradle/process/**",
            "org/gradle/testfixtures/**",
            "org/gradle/testing/jacoco/**",
            "org/gradle/tooling/**",
            "org/gradle/swiftpm/**",
            "org/gradle/model/**",
            "org/gradle/testkit/**",
            "org/gradle/testing/**",
            "org/gradle/vcs/**",
            "org/gradle/work/**",
            "org/gradle/workers/**",
            "org/gradle/util/**"
        ).map(TypeOrigin::toPackagePattern)
        .collect(ImmutableList.toImmutableList());

    private static final List<Pattern> INTERNAL_PACKAGES = Stream.of(
            "**/internal/**",
            "net/rubygrapefruit/**"
        ).map(TypeOrigin::toPackagePattern)
        .collect(ImmutableList.toImmutableList());
    private final boolean gradleApi;

    private static Pattern toPackagePattern(String packageGlob) {
        return Pattern.compile("L" + packageGlob
            .replaceAll("\\*\\*", "###")
            .replaceAll("/\\*", "/[A-Z][a-z_A-Z0-9]+")
            .replaceAll("###", ".*?")
        );
    }

    private static final List<Atom> GRADLE_ROOTS = atoms(
        "org/gradle",
        "net/rubygrapefruit"
    );
    private static final List<Atom> RUNTIME_ROOTS = atoms(
        "java", "javax", "jdk",
        "groovy", "org/codehaus/groovy",
        "kotlin"
    );

    private static List<Atom> atoms(String... names) {
        return Stream.of(names)
            .map(Atom::findOrCreateAsciiAtom)
            .collect(ImmutableList.toImmutableList());
    }

    private static final LoadingCache<TypeName, TypeOrigin> CACHE = CacheBuilder.newBuilder()
        .build(new CacheLoader<>() {
            @Nonnull
            @Override
            public TypeOrigin load(TypeName type) {
                if (type.isArrayType()) {
                    type = type.getInnermostElementType();
                }
                if (type.isPrimitiveType()) {
                    return RUNTIME;
                }
                Atom pkg = type.getPackage();
                if (pkg == null) {
                    return EXTERNAL;
                } else if (GRADLE_ROOTS.stream().anyMatch(pkg::startsWith)) {
                    String className = type.toString();
                    if (INTERNAL_PACKAGES.stream().noneMatch(pattern -> matches(pattern, className))
                        && PUBLIC_PACKAGES.stream().anyMatch(pattern -> matches(pattern, className))) {
                        return PUBLIC;
                    } else {
                        return INTERNAL;
                    }
                } else if (RUNTIME_ROOTS.stream().anyMatch(pkg::startsWith)) {
                    return RUNTIME;
                } else {
                    return EXTERNAL;
                }
            }
        });

    private static boolean matches(Pattern pattern, String packageName) {
        return pattern.matcher(packageName).matches();
    }

    public static TypeOrigin of(TypeName type) {
        return CACHE.getUnchecked(type);
    }

    public static TypeOrigin of(TypeReference reference) {
        return of(reference.getName());
    }

    public static TypeOrigin of(IClass clazz) {
        return of(clazz.getName());
    }

    public boolean isGradleApi() {
        return gradleApi;
    }

    TypeOrigin(boolean gradleApi) {
        this.gradleApi = gradleApi;
    }

    public static boolean isGradleApi(IClass clazz) {
        return of(clazz).isGradleApi();
    }

    public static boolean isPublicGradleApi(IClass clazz) {
        return of(clazz) == PUBLIC;
    }

    public static boolean isExternal(IClass clazz) {
        return of(clazz) == EXTERNAL;
    }
}
