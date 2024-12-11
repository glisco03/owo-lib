package io.wispforest.owo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import io.wispforest.owo.Owo;
import io.wispforest.owo.util.StackTraceSupplier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(value = DataResult.class, remap = false)
public interface DataResultMixin {

    @Inject(
            method = {
                    "error(Ljava/util/function/Supplier;)Lcom/mojang/serialization/DataResult;",
                    "error(Ljava/util/function/Supplier;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
                    "error(Ljava/util/function/Supplier;Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/serialization/DataResult;",
                    "error(Ljava/util/function/Supplier;Ljava/lang/Object;Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/serialization/DataResult;"
            },
            at = @At(value = "HEAD"),
            remap = false
    )
    private static <R> void wrapMessageWithStacktrace(CallbackInfoReturnable<Optional<DataResult.Error<R>>> cir, @Local(argsOnly = true) LocalRef<Supplier<String>> messageSupplier) {
        if (!Owo.DEBUG) return;

        var ogSupplier = messageSupplier.get();
        var ogClass = ogSupplier.getClass();
        if (ogSupplier instanceof StackTraceSupplier) return;

        StackTraceSupplier stackTraceSupplier = null;

        if (ogClass.isSynthetic()) {
            try {
                for (var field : ogClass.getDeclaredFields()) {
                    if (!Throwable.class.isAssignableFrom(field.getType())) continue;

                    field.setAccessible(true);
                    if (field.get(ogSupplier) instanceof Throwable e) {
                        stackTraceSupplier = StackTraceSupplier.of(e, ogSupplier);
                    }
                    break;
                }
            } catch (IllegalArgumentException | IllegalAccessException ignore) {}
        }

        if (stackTraceSupplier == null) stackTraceSupplier = StackTraceSupplier.of(ogSupplier.get());

        messageSupplier.set(stackTraceSupplier);
    }

    @Mixin(value = DataResult.Error.class, remap = false)
    abstract class DataResultErrorMixin<R> {

        @Unique
        private static final Logger LOGGER = LogUtils.getLogger();

        @Shadow(remap = false)
        public abstract Supplier<String> messageSupplier();

        @Shadow @Final
        private Supplier<String> messageSupplier;

        @Inject(method = {"getOrThrow", "getPartialOrThrow"}, at = @At(value = "HEAD"), remap = false)
        private <E extends Throwable> void addStackTraceToException(CallbackInfoReturnable<R> cir, @Local(argsOnly = true) LocalRef<Function<String, E>> exceptionSupplier) {
            final var funcToWrap = exceptionSupplier.get();

            exceptionSupplier.set(s -> {
                var exception = funcToWrap.apply(s);
                if (this.messageSupplier() instanceof StackTraceSupplier stackTraceSupplier) {
                    exception.setStackTrace(stackTraceSupplier.getFullStackTrace());
                }
                return exception;
            });
        }

        @WrapOperation(method ={
                "resultOrPartial(Ljava/util/function/Consumer;)Ljava/util/Optional;",
                "promotePartial"
        }, at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
        private <T> void printStackTrace(Consumer<T> instance, T t, Operation<Void> original) {
            original.call(instance, t);

            if (Owo.DEBUG && this.messageSupplier instanceof StackTraceSupplier supplier) {
                LOGGER.error("An error has occurred within DFU: ", supplier.throwable());
            }
        }

        @WrapOperation(method = {
                "ap(Lcom/mojang/serialization/DataResult;)Lcom/mojang/serialization/DataResult$Error;",
                "flatMap(Ljava/util/function/Function;)Lcom/mojang/serialization/DataResult$Error;"
        }, at = @At(value = "NEW", target = "(Ljava/util/function/Supplier;Ljava/util/Optional;Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/serialization/DataResult$Error;", ordinal = 1))
        private DataResult.Error preserveStackTrace1(Supplier<String> messageSupplier, Optional partialValue, Lifecycle lifecycle, Operation<DataResult.Error> original) {
            if (this.messageSupplier instanceof StackTraceSupplier supplier) {
                messageSupplier = StackTraceSupplier.of(supplier.throwable(), messageSupplier);
            }

            return original.call(messageSupplier, partialValue, lifecycle);
        }

        @WrapOperation(method = {
                "mapError(Ljava/util/function/UnaryOperator;)Lcom/mojang/serialization/DataResult$Error;"
        }, at = @At(value = "NEW", target = "(Ljava/util/function/Supplier;Ljava/util/Optional;Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/serialization/DataResult$Error;"))
        private DataResult.Error preserveStackTrace2(Supplier<String> messageSupplier, Optional partialValue, Lifecycle lifecycle, Operation<DataResult.Error> original) {
            if (this.messageSupplier instanceof StackTraceSupplier supplier) {
                messageSupplier = StackTraceSupplier.of(supplier.throwable(), messageSupplier);
            }

            return original.call(messageSupplier, partialValue, lifecycle);
        }
    }
}
