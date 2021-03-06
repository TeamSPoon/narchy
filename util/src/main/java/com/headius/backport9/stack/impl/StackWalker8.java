package com.headius.backport9.stack.impl;

import com.headius.backport9.stack.StackWalker;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

public class StackWalker8 implements StackWalker {
    public StackWalker8() {
    }

    @Override
    public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        // start at 1 to remove this frame from the walk
        return walkElements(stackTrace, Math.min(1, stackTrace.length), stackTrace.length, function);
    }

    public static <T> T walkElements(StackTraceElement[] trace, int startInclusive, int endExclusive, Function<? super Stream<StackFrame>, ? extends T> function) {
        Stream<StackFrame> stream = Arrays.stream(trace, startInclusive, endExclusive).map(element -> new StackFrame8(element));
        return function.apply(stream);
    }

    public static class StackFrame8 implements StackFrame {
        final StackTraceElement element;

        public StackFrame8(StackTraceElement element) {
            this.element = element;
        }

        @Override
        public String getClassName() {
            return element.getClassName();
        }

        @Override
        public String getMethodName() {
            return element.getMethodName();
        }

        @Override
        public Class<?> getDeclaringClass() {
            return null;
        }

        @Override
        public int getByteCodeIndex() {
            return -1;
        }

        @Override
        public String getFileName() {
            return element.getFileName();
        }

        @Override
        public int getLineNumber() {
            return element.getLineNumber();
        }

        @Override
        public boolean isNativeMethod() {
            return element.isNativeMethod();
        }

        @Override
        public StackTraceElement toStackTraceElement() {
            return element;
        }

        /**
         * Returns the <i>descriptor</i> of the method represented by
         * this stack frame as defined by
         * <cite>The Java Virtual Machine Specification</cite>.
         *
         * @implSpec
         * The default implementation throws {@code UnsupportedOperationException}.
         *
         * @return the descriptor of the method represented by
         *         this stack frame
         *
         * @see MethodType#fromMethodDescriptorString(String, ClassLoader)
         * @see MethodType#toMethodDescriptorString()
         * @jvms 4.3.3 Method Descriptor
         *
         * @since 10
         */		

		@Override
		public java.lang.String getDescriptor() {
			// @TODO dmiles look up mathod and return signature
			return element.getMethodName();
		}
		
		
    }
}
