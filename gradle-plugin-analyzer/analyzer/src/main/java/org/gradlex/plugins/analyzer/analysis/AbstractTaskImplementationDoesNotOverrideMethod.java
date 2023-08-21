package org.gradlex.plugins.analyzer.analysis;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMember;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.shrike.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrike.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrike.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrike.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeReference;
import org.gradlex.plugins.analyzer.ExternalSubtypeAnalysis;
import org.gradlex.plugins.analyzer.TypeOrigin;
import org.slf4j.event.Level;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AbstractTaskImplementationDoesNotOverrideMethod extends ExternalSubtypeAnalysis {
    private final String methodType;
    private final Predicate<? super IMethod> filter;

    public AbstractTaskImplementationDoesNotOverrideMethod(String methodType, Predicate<? super IMethod> filter) {
        super("Lorg/gradle/api/Task");
        this.methodType = methodType;
        this.filter = filter;
    }

    @Override
    protected void analyzeType(IClass type, AnalysisContext context) {
        type.getDeclaredMethods().stream()
            // Ignore bridge methods
            .filter(Predicate.not(IMethod::isBridge))
            // Looking for instance methods
            .filter(Predicate.not(IMember::isStatic))
            // Looking for public or protected methods
            .filter(method -> method.isPublic() || method.isProtected())
            // Matching our filter
            .filter(filter)
            // Walk ancestry
            .forEach(method -> Stream.iterate(type.getSuperclass(), Objects::nonNull, IClass::getSuperclass)
                // We only care about methods that come from the Gradle API
                .filter(TypeOrigin::isGradleApi)
                // Find the same method in the superclass
                .flatMap(clazz -> Stream.ofNullable(clazz.getMethod(method.getSelector())))
                .findFirst()
                .ifPresent(overriddenMethod -> reportOverriddenMethod(context, type, method, overriddenMethod))
            );
    }

    private void reportOverriddenMethod(AnalysisContext context, IClass type, IMethod method, IMethod overriddenMethod) {
        ShrikeCTMethod methodImpl = (ShrikeCTMethod) method;
        try {
            checkOverridingInstructions(methodImpl);
        } catch (InvalidClassFileException e) {
            throw new RuntimeException(e);
        } catch (AnalysisException ex) {
            context.report(Level.WARN, String.format("The %s %s() in %s overrides Gradle API from %s with custom logic: %s",
                methodType, method.getName(), type.getName(), overriddenMethod.getDeclaringClass().getName(), ex.getMessage()));
            return;
        }
        context.report(Level.INFO, String.format("The %s %s() in %s overrides Gradle API from %s, but calls only super()",
            methodType, method.getName(), type.getName(), overriddenMethod.getDeclaringClass().getName()));
    }

    private static void checkOverridingInstructions(ShrikeCTMethod method) throws InvalidClassFileException, AnalysisException {
        InstructionQueue queue = new InstructionQueue(method.getInstructions());

        queue.takeNextIf(IInvokeInstruction.class, invokeCallSiteArray ->
                invokeCallSiteArray.getInvocationCode() == Dispatch.STATIC
                && invokeCallSiteArray.getMethodName().equals("$getCallSiteArray"))
            .ifPresentOrElse(
                invokeInstruction -> checkDynamicGroovyInstructions(method, queue),
                () -> checkJavaInstructions(method, queue)
            );

        queue.expectNoMore();
    }

    private static void checkJavaInstructions(ShrikeCTMethod method, InstructionQueue queue) throws AnalysisException {
        ILoadInstruction iLoad = queue.expectNext(ILoadInstruction.class);
        if (!iLoad.getType().equals("Ljava/lang/Object;")) {
            throw new AnalysisException("Load instruction has wrong type: %s", iLoad.getType());
        }
        if (iLoad.getVarIndex() != 0) {
            throw new AnalysisException("Load instruction has wrong var index: %s", iLoad.getVarIndex());
        }

        for (int paremNo = 0; paremNo < method.getNumberOfParameters() - 1; paremNo++) {
            queue.expectNext(ILoadInstruction.class);
        }

        IInvokeInstruction iInvoke = queue.expectNext(IInvokeInstruction.class);
        // Remove type prefix + '.'
        String expectedMethodSignature = method.getSignature().substring(method.getDeclaringClass().getName().toString().length());
        String invokedMethodSignature = iInvoke.getMethodName() + iInvoke.getMethodSignature();
        if (!invokedMethodSignature.equals(expectedMethodSignature)) {
            throw new AnalysisException("Invokes different method, expected: %s, got: %s", expectedMethodSignature, invokedMethodSignature);
        }
        if (!iInvoke.getInvocationCode().equals(Dispatch.SPECIAL)) {
            throw new AnalysisException("Invoke instruction is not special: %s", iInvoke);
        }

        queue.expectNext(ReturnInstruction.class);
    }

    private static void checkDynamicGroovyInstructions(ShrikeCTMethod method, InstructionQueue queue) throws AnalysisException {
        // Process remaining of Groovy method init
        queue.expectNext(IStoreInstruction.class, iStore ->
            iStore.getType().equals(TypeReference.JavaLangObject.getName() + ";")
            && iStore.getVarIndex() == 1);
        queue.expectNext(ConstantInstruction.class);


    }

    private static class InstructionQueue {
        private final Queue<IInstruction> instructions;
        int counter = 0;

        public InstructionQueue(IInstruction... instructions) {
            Arrays.stream(instructions)
                .forEach(System.out::println);
            this.instructions = new ArrayDeque<>(ImmutableList.copyOf(instructions));
        }

        public <I extends IInstruction> Optional<I> takeNextIf(Class<I> type) throws AnalysisException {
            return takeNextIf(type, Predicates.alwaysTrue());
        }

        public <I extends IInstruction> Optional<I> takeNextIf(Class<I> type, Predicate<? super I> matcher) throws AnalysisException {
            IInstruction next = instructions.peek();
            if (next == null) {
                throw new AnalysisException("No more instruction after #%d", counter);
            }
            if (type.isInstance(next)) {
                I typedNext = type.cast(next);
                if (matcher.test(typedNext)) {
                    counter++;
                    instructions.poll();
                    return Optional.of(typedNext);
                }
            }
            return Optional.empty();
        }

        public <I extends IInstruction> I expectNext(Class<I> type) throws AnalysisException {
            return expectNext(type, Predicates.alwaysTrue());
        }

        public <I extends IInstruction> I expectNext(Class<I> type, Predicate<? super I> matcher) throws AnalysisException {
            IInstruction next = instructions.poll();
            if (next == null) {
                throw new AnalysisException("No more instruction after #%d", counter);
            }
            counter++;
            if (!type.isInstance(next)) {
                throw new AnalysisException("Instruction #%d expected to be %s but it was %s",
                    counter, type.getSimpleName(), next);
            }
            I typedNext = type.cast(next);
            if (!matcher.test(typedNext)) {
                throw new AnalysisException("Instruction #%d (%s) had unexpected parameters: %s", counter, type.getSimpleName(), next);
            }
            return typedNext;
        }

        public void expectNoMore() throws AnalysisException {
            if (!instructions.isEmpty()) {
                throw new AnalysisException("Expected no more instructions after #%d but there are %d more", counter, instructions.size());
            }
        }
    }

    private static class AnalysisException extends RuntimeException {
        public AnalysisException(String message) {
            super(message);
        }

        public AnalysisException(String message, Object... params) {
            this(String.format(message, params));
        }
    }
}
