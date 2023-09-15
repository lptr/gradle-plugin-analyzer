package org.gradlex.plugins.analyzer;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import org.gradlex.plugins.analyzer.TypeReferenceWalker.VisitDecision;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class WalaUtil {
    public static boolean matches(Pattern pattern, Atom name) {
        return pattern.matcher(name.toString()).matches();
    }

    public static IInstruction[] instructions(IMethod method) {
        try {
            IInstruction[] instructions = ((ShrikeCTMethod) method).getInstructions();
            // TODO Why can't we sometimes get the instructions?
            return Objects.requireNonNullElseGet(instructions, () -> new IInstruction[0]);
        } catch (InvalidClassFileException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean matchesType(Supplier<String> typeName, TypeReference reference) {
        return matchesType(typeName.get(), reference);
    }

    public static boolean matchesType(String typeName, TypeReference reference) {
        return typeName.equals(reference.getName().toString() + ";");
    }

    public static <T> void visitHierarchy(T seed, Function<? super T, Stream<? extends T>> visitor) {
        var seen = new HashSet<>();
        visitHierarchy(seed, seen::add, visitor);
    }

    public static <T> void visitHierarchy(T seed, Predicate<? super T> seen, Function<? super T, Stream<? extends T>> visitor) {
        if (!seen.test(seed)) {
            return;
        }

        var queue = new ArrayDeque<T>();
        queue.add(seed);
        while (true) {
            T node = queue.poll();
            if (node == null) {
                break;
            }
            visitor.apply(node)
                .filter(seen)
                .forEach(queue::add);
        }
    }

    public static <T> void visitTypeHierarchy(IClass baseType, Predicate<IClass> processor) {
        WalaUtil.visitHierarchy(baseType, type -> directSuperTypes(type)
            .filter(processor));
    }

    private static Stream<IClass> directSuperTypes(IClass type) {
        return Stream.concat(
            Stream.ofNullable(type.getSuperclass()),
            type.getDirectInterfaces().stream());
    }

    public static void visitSupertypes(IClass baseType, Function<IClass, VisitDecision> hierarchyFilter, Consumer<IClass> processor) {
        visitTypeHierarchy(baseType, superType -> {
            VisitDecision decision = hierarchyFilter.apply(superType);
            if (decision == VisitDecision.STOP_DONT_VISIT) {
                return false;
            }
            processor.accept(superType);
            return decision == VisitDecision.VISIT_AND_CONTINUE;
        });
    }

    public static String toFQCN(String internalType) {
        switch (internalType) {
            case "B" -> {
                return "byte";
            }
            case "C" -> {
                return "char";
            }
            case "D" -> {
                return "double";
            }
            case "F" -> {
                return "float";
            }
            case "I" -> {
                return "int";
            }
            case "J" -> {
                return "long";
            }
            case "S" -> {
                return "short";
            }
            case "Z" -> {
                return "boolean";
            }
            case "V" -> {
                return "void";
            }
        }

        if (!internalType.startsWith("L")) {
            throw new IllegalArgumentException("Invalid internal type notation: " + internalType);
        }
        var loseRight = internalType.endsWith(";") ? 1 : 0;
        return internalType.substring(1, internalType.length() - loseRight).replace('/', '.');
    }

    public static String toFQCN(TypeName name) {
        if (name.isArrayType()) {
            return toFQCN(name.parseForArrayElementName()) + "[]";
        } else {
            return toFQCN(name.toString());
        }
    }

    public static String toFQCN(TypeReference reference) {
        return toFQCN(reference.getName());
    }

    public static String toFQCN(IClass type) {
        return toFQCN(type.getName());
    }
}
