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
            if (this instanceof TypeSource) {
                return typeHandler.apply(((TypeSource) this).type());
            }
            if (this instanceof FieldSource) {
                return fieldHandler.apply(((FieldSource) this).field());
            }
            if (this instanceof MethodSource) {
                return methodHandler.apply(((MethodSource) this).method());
            }
            throw new AssertionError();
        }
    }

    public sealed interface Target {
        default <T> T map(Function<? super TypeReference, ? extends T> typeHandler,
                          Function<? super IMethod, ? extends T> methodHandler) {
            if (this instanceof TypeTarget) {
                return typeHandler.apply(((TypeTarget) this).type());
            }
            if (this instanceof MethodTarget) {
                return methodHandler.apply(((MethodTarget) this).method());
            }
            throw new AssertionError();
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
        Target refTarget = reference.target();
        Object target;
        if (refTarget instanceof TypeTarget) {
            target = ((TypeTarget) refTarget).type();
        } else if (refTarget instanceof MethodTarget) {
            target = ((MethodTarget) refTarget).method();
        } else {
            throw new AssertionError();
        }

        Source source = reference.source();
        if (source instanceof TypeDeclarationSource) {
            return new Message("The %s references %s",
                ((TypeDeclarationSource) source).type(), target);
        }
        if (source instanceof TypeInheritanceSource) {
            return new Message("The %s extends %s",
                ((TypeInheritanceSource) source).type(), target);
        }
        if (source instanceof FieldDeclarationSource) {
            return new Message("The %s references %s",
                ((FieldDeclarationSource) source).field(), target);
        }
        if (source instanceof MethodDeclarationSource) {
            return new Message("The declaration of %s references %s",
                ((MethodDeclarationSource) source).method(), target);
        }
        if (source instanceof MethodInheritanceSource) {
            return new Message("The %s overrides %s",
                ((MethodInheritanceSource) source).method(), target);
        }
        if (source instanceof MethodBodySource) {
            return new Message("The %s references %s",
                ((MethodBodySource) source).method(), target);
        }
        throw new AssertionError();
    }
}
