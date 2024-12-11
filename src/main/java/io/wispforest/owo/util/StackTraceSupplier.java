package io.wispforest.owo.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class StackTraceSupplier implements Supplier<String> {
    private final Throwable throwable;
    private final @Nullable Supplier<String> message;

    private StackTraceSupplier(@Nullable Throwable throwable, @Nullable Supplier<String> message) {
        this.throwable = throwable;
        this.message = message;
    }

    public static StackTraceSupplier of(Throwable throwable) {
        return new StackTraceSupplier(throwable, null);
    }

    public static StackTraceSupplier of(Throwable throwable, Supplier<String> supplier) {
        return new StackTraceSupplier(throwable, supplier);
    }

    public static StackTraceSupplier of(String message) {
        return new StackTraceSupplier(new IllegalStateException(message), null);
    }

    @Override
    public String get() {
        return message != null ? message.get() : throwable.getMessage();
    }

    public StackTraceElement[] getFullStackTrace() {
        var innerThrowable = throwable();
        while (innerThrowable.getCause() != null) {
            innerThrowable = throwable().getCause();
        }
        return innerThrowable.getStackTrace();
    }

    public Throwable throwable() {
        return throwable;
    }

    @Override
    public String toString() {
        return "StackTraceSupplier[" +
                "throwable=" + throwable + ", " +
                "message=" + message + ']';
    }
}
