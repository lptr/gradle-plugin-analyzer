package org.gradlex.plugins.analyzer;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMember;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeReference;
import org.gradlex.plugins.analyzer.Reporter.Message;

import java.util.function.Function;
import java.util.function.Predicate;

public record Reference(Source source, Target target) {
    public sealed interface Source {
        default <T> T map(Function<? super IClass, ? extends T> typeHandler,
                          Function<? super IField, ? extends T> fieldHandler,
                          Function<? super IMethod, ? extends T> methodHandler) {
            return switch (this) {
                case TypeSource it -> typeHandler.apply(it.type());
                case FieldSource it -> fieldHandler.apply(it.field());
                case MethodSource it -> methodHandler.apply(it.method());
            };
        }
    }

    public sealed interface Target {
        default <T> T map(Function<? super TypeReference, ? extends T> typeHandler,
                          Function<? super IMethod, ? extends T> methodHandler) {
            return switch (this) {
                case TypeTarget it -> typeHandler.apply(it.type());
                case MethodTarget it -> methodHandler.apply(it.method());
            };
        }
    }

    public static Predicate<Reference> sourceIs(TypeOrigin origin) {
        return reference -> TypeOrigin.of(reference.source.map(
            Function.identity(),
            IMember::getDeclaringClass,
            IMember::getDeclaringClass
        )) == origin;
    }

    public static Predicate<Reference> targetIs(TypeOrigin origin) {
        return reference -> TypeOrigin.of(reference.target.map(
            Function.identity(),
            method -> method.getDeclaringClass().getReference()
        )) == origin;
    }

    public sealed interface TypeSource extends Source {
        IClass type();
    }

    public sealed interface FieldSource extends Source {
        IField field();
    }

    public sealed interface MethodSource extends Source {
        IMethod method();
    }

    public record TypeDeclarationSource(IClass type) implements TypeSource {
    }

    public record TypeInheritanceSource(IClass type) implements TypeSource {
    }

    public record FieldDeclarationSource(IField field) implements FieldSource {
    }

    public record MethodDeclarationSource(IMethod method) implements MethodSource {
    }

    public record MethodInheritanceSource(IMethod method) implements MethodSource {
    }

    public record MethodBodySource(IMethod method) implements MethodSource {
    }

    public record TypeTarget(TypeReference type) implements Target {
    }

    public record MethodTarget(IMethod method) implements Target {
    }

    public static Message format(Reference reference) {
        Object target = switch (reference.target()) {
            case TypeTarget it -> it.type();
            case MethodTarget it -> it.method();
        };

        return switch (reference.source()) {
            case TypeDeclarationSource it -> new Message("The %s references %s", it.type(), target);
            case TypeInheritanceSource it -> new Message("The %s extends %s", it.type(), target);
            case FieldDeclarationSource it -> new Message("The %s references %s", it.field(), target);
            case MethodDeclarationSource it -> new Message("The declaration of %s references %s", it.method(), target);
            case MethodInheritanceSource it -> new Message("The %s overrides %s", it.method(), target);
            case MethodBodySource it -> new Message("The %s references %s", it.method(), target);
        };
    }
}
