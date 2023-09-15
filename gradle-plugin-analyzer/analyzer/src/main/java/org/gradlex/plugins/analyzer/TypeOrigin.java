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

    private static final PackageMatcher PUBLIC_MATCHER = new CompositePackageMatcher(
        exact("org/gradle"),
        prefix("org/gradle/api"),
        prefix("org/gradle/authentication"),
        prefix("org/gradle/build"),
        prefix("org/gradle/buildinit"),
        prefix("org/gradle/caching"),
        prefix("org/gradle/concurrent"),
        prefix("org/gradle/deployment"),
        prefix("org/gradle/env"),
        prefix("org/gradle/external/javadoc"),
        prefix("org/gradle/ide"),
        prefix("org/gradle/includedbuild"),
        prefix("org/gradle/ivy"),
        prefix("org/gradle/jvm"),
        prefix("org/gradle/language"),
        prefix("org/gradle/maven"),
        prefix("org/gradle/nativeplatform"),
        prefix("org/gradle/normalization"),
        prefix("org/gradle/platform"),
        prefix("org/gradle/play"),
        prefix("org/gradle/plugin/devel"),
        exact("org/gradle/plugin/repository"),
        exact("org/gradle/plugin/use"),
        exact("org/gradle/plugin/management"),
        prefix("org/gradle/plugins"),
        prefix("org/gradle/process"),
        prefix("org/gradle/testfixtures"),
        prefix("org/gradle/testing/jacoco"),
        prefix("org/gradle/tooling"),
        prefix("org/gradle/swiftpm"),
        prefix("org/gradle/model"),
        prefix("org/gradle/testkit"),
        prefix("org/gradle/testing"),
        prefix("org/gradle/vcs"),
        prefix("org/gradle/work"),
        prefix("org/gradle/workers"),
        prefix("org/gradle/util")
    );

    private final boolean gradleApi;

    private static final List<Atom> GRADLE_ROOTS = atoms(
        "org/gradle",
        "net/rubygrapefruit"
    );
    private static final List<Atom> RUNTIME_ROOTS = atoms(
        "java", "javax", "jdk",
        "groovy", "org/codehaus/groovy",
        "kotlin",
        // This is to avoid detecting references in org.slf4j.impl.StaticLoggerBinder as internal API
        "org/slf4j"
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
                    return PUBLIC_MATCHER.match(pkg) ? PUBLIC : INTERNAL;
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

    public static boolean any(@SuppressWarnings("unused") IClass type) {
        return true;
    }

    private interface PackageMatcher {
        boolean match(Atom pkg);
    }

    private static PackageMatcher exact(String name) {
        return new ExactPackageMatcher(name);
    }

    private static PackageMatcher prefix(String name) {
        return new PrefixPackageMatcher(name);
    }

    private static class ExactPackageMatcher implements PackageMatcher {
        private final Atom name;

        public ExactPackageMatcher(String name) {
            this.name = Atom.findOrCreateAsciiAtom(name);
        }

        @Override
        public boolean match(Atom pkg) {
            return pkg == name;
        }
    }

    private static class PrefixPackageMatcher extends ExactPackageMatcher {
        private final Atom prefix;

        public PrefixPackageMatcher(String name) {
            super(name);
            this.prefix = Atom.findOrCreateAsciiAtom(name + "/");
        }

        @Override
        public boolean match(Atom pkg) {
            return super.match(pkg) || (pkg.startsWith(prefix) && !internal(pkg.toString()));
        }

        private boolean internal(String pkg) {
            return pkg.endsWith("/internal") || pkg.contains("/internal/");
        }
    }

    private static class CompositePackageMatcher implements PackageMatcher {
        private final List<PackageMatcher> matchers;

        public CompositePackageMatcher(PackageMatcher... matchers) {
            this.matchers = ImmutableList.copyOf(matchers);
        }

        @Override
        public boolean match(Atom pkg) {
            return matchers.stream().anyMatch(matcher -> matcher.match(pkg));
        }
    }
}
