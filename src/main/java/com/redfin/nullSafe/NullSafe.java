package com.redfin.nullSafe;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is a utility class that provides null-safe access to nested fields, similar to that implicitly supported in
 * other languages such as Swift. It's preferable to another workaround to Java's limited support for null-safe
 * access, filtering out nulls via Streams, as this library provides:
 * <ol>
 *     <li><b>Quality:</b> The null checks are embedded within this library itself, making it more difficult to check in
 *     buggy code.</li>
 *     <li><b>Readable:</b> It's obvious what the caller is doing, as the purpose is intrinsic in this library's
 *     name and syntax.</li>
 *     <li><b>Flexible:</b> This library supports the invocation of optional null/error handlers.</li>
 * </ol>
 * <p/>
 * Use this library in cases where you need to access a deeply nested field. For example, instead of the following:
 * <pre>
 *     ClassA a = getA();
 *     if (a == null) {
 *        return null;
 *     }
 *     ClassB b = a.getB();
 *     if (b == null) {
 *        return null;
 *     }
 *     ClassC c = b.getC();
 *     if (c == null) {
 *         return null;
 *     }
 *     return c.getSomeField();
 * </pre>
 * <p/>
 * You can instead write code like the following:
 * <pre>
 *     return NullSafe.from(a)
 *                    .access(ClassA::getB)
 *                    .access(ClassB::getC)
 *                    .access(ClassC::getSomeField)
 *                    .get();
 * </pre>
 * The library will return null if the first "root-node" data (in the case above, the result from {@code getAgent()}),
 * or any subsequently fetched data from the provided functions, evaluates to null. You can also specify custom null
 * handlers at any level of the chain. The latter-most null handler will catch any nulls that preceded it. (The default
 * null handler simply returns null.)
 * <pre>
 *     return NullSafe.from(agent)
 *                    .ifNull(() -> { throw new IllegalArgumentException("agent must be non-null"); })
 *                    .access(Agent::getPerson)
 *                    .access(Person::getLogin)
 *                    .ifNull(() -> defaultLogin)
 *                    .access(Login::getPrimaryEmail)
 *                    .ifNull(() -> { throw new IllegalStateException("agent is missing an email address: " + agent); })
 *                    .get();
 * </pre>
 * Accessor methods are invoked on demand—that is only once {@link Accessor#get()} is called.
 */
public class NullSafe {

    private NullSafe() {
        // private constructor to prevent instantiation
    }

    /**
     * Create a new {@link Data} from source data from which nested data can be accessed in a null-safe manner.
     *
     * @param data the data from which nested data will be accessed
     * @param <SOURCE> the type of this root/source data
     * @return a new {@link Data} instance
     */
    public static <SOURCE> Data<SOURCE> from(SOURCE data) {
        return new Data<>(data);
    }

    /**
     * Same as {@link #from(Object)} except that the {@link Optional} will be unwrapped at the time of instantiation
     * and provided to any subsequent {@link Accessor} in its unwrapped (or null) form.
     *
     * @param data the data from which nested data will be accessed
     * @param <SOURCE> the type of this root/source data
     * @return a new {@link Data} instance
     */
    public static <SOURCE> Data<SOURCE> from(Optional<SOURCE> data) {
        return new Data<>(data.orElse(null));
    }

    /**
     * A container for the root/source data from which one can make null-safe accesses.
     * <p/>
     * <b>Q: Why do we need a separate data class rather than just instantiating a {@link NullSafe.Accessor}
     * directly?</b> (Stop reading here if you are not interested in learning about the design decisions that underly
     * this library.)
     * <p/>
     * <b>A: We can't infer the output type, which the accessor requires, until we've been given the function to be
     * called on the provided data.</b> For example, the following does not compile without sacrificing type safety:
     * <pre>{@code
     *     NullSafe.of(data) // returns Accessor<Input, ?>
     *             .access(method1) // returns Accessor<?, Output1>
     *             .access(method2) // returns Accessor<Output1, Output2>
     * }</pre>
     * because we don't know the output type of the first accessor in the chain. Such code will yield an error like the
     * following:
     * <blockquote>
     *     no instance(s) of type variable(s) exist so that capture of ? conforms to Input
     * </blockquote>
     * <p/>
     * So without this initial {@link NullSafe.Data} class in the chain, the first method in the accessor
     * chain would have to look something like:
     * <pre>{@code
     *     NullSafe.of(data, method1) // returns Accessor<Input, Output1>
     *             .access(method2) // returns Accessor<Output1, Output2>
     * }</pre>
     * Beyond the fact that such a style makes it impossible to insert a null handler between {@code data} and
     * {@code method1} (e.g. {@code NullSafe.of(data).ifNull(f).access(method1)}), such a style disrupts the syntactic
     * intent of the library in that the caller must switch between one style of accessing data (with the first method)
     * and another (for subsequent methods).
     * <p/>
     * Therefore, the library is designed to provide null-safe accesses beginning with this {@link NullSafe.Data} class
     * as follows, where {@code data} is of type {@code Input}; {@code method1} returns type {@code Output1}; and
     * {@code method2} returns type {@code Output2}:
     * <pre>{@code
     *     NullSafe.of(data) // returns Data<Input>
     *             .access(method1) // returns Accessor<Input, Output1>
     *             .access(method2) // returns Accessor<Output1, Output2>
     * }</pre>
     *
     * @param <SOURCE> the type of data from which nested data will be accessed
     */
    public static class Data<SOURCE> {
        private final SOURCE data;

        private Data(SOURCE data) {
            this.data = data;
        }

        /**
         * Add a null handler to be invoked in case this data is null.
         *
         * @param nullHandler a supplier for fall-back data (if desired) that is invoked if this data is null
         * @return a new accessor
         */
        public Accessor<SOURCE, SOURCE> ifNull(Supplier<SOURCE> nullHandler) {
            return new Accessor<>(() -> data, a -> data, nullHandler);
        }

        /**
         * Access a nested field from this data.
         *
         * @param accessorFunction a function to access a nested field from this data
         * @param <OUTPUT> the output type of the new accessor
         * @return a new accessor
         */
        public <OUTPUT> Accessor<SOURCE, OUTPUT> access(Function<SOURCE, OUTPUT> accessorFunction) {
            return new Accessor<>(() -> data, accessorFunction, () -> null);
        }

        /**
         * Same as {@link #access(Function)} except used to unwrap {@link Optional} values returned from the provided
         * {@code accessorFunction}.
         *
         * @param accessorFunction a function to access a nested {@link Optional} field from this data
         * @param <OUTPUT> the output type of the new accessor
         * @return a new accessor
         */
        public <OUTPUT> Accessor<SOURCE, OUTPUT> accessAndUnwrapOptional(
                Function<SOURCE, Optional<OUTPUT>> accessorFunction) {
            return new Accessor<>(() -> data, getUnwrappedAccessorFunction(accessorFunction), () -> null);
        }
    }

    public static class Accessor<INPUT, OUTPUT> {
        private final Supplier<INPUT> dataSupplier;
        private Function<INPUT, OUTPUT> accessorFunction;
        private Supplier<OUTPUT> nullHandler;

        private Accessor(Supplier<INPUT> dataSupplier, Function<INPUT, OUTPUT> accessorFunction,
                         Supplier<OUTPUT> nullHandler) {
            this.dataSupplier = dataSupplier;
            this.accessorFunction = accessorFunction;
            this.nullHandler = nullHandler;
        }

        /**
         * Set the null handler for this accessor such that if the data retrieved by this accessor is null, rather than
         * returning null (the default behavior), the null handler will be invoked, and its return value will be
         * returned instead.
         *
         * @param nullHandler a supplier for fall-back data (if desired) that is invoked if this accessor's data is null
         * @return this accessor
         */
        public Accessor<INPUT, OUTPUT> ifNull(Supplier<OUTPUT> nullHandler) {
            this.nullHandler = nullHandler;
            return this;
        }

        /**
         * @return the nested data from this accessor
         */
        public OUTPUT get() {
            INPUT input = dataSupplier.get();
            if (input == null) {
                return nullHandler.get();
            }
            OUTPUT output = accessorFunction.apply(input);
            if (output == null) {
                return nullHandler.get();
            }
            return output;
        }

        /**
         * @return the nested data from this accessor wrapped in a nullable {@link Optional}
         */
        public Optional<OUTPUT> toOptional() {
            return Optional.ofNullable(get());
        }

        /**
         * Use this method to chain a new nested accessor to the current accessor.
         *
         * @param chainedAccessorFunction a function which will access nested data from the current accessor's result
         * @param <CHAINED_OUTPUT> the output type of the new chained accessor
         * @return a new chained accessor
         */
        public <CHAINED_OUTPUT> Accessor<OUTPUT, CHAINED_OUTPUT> access(
                Function<OUTPUT, CHAINED_OUTPUT> chainedAccessorFunction) {
            return new Accessor<>(this::get, chainedAccessorFunction, () -> null);
        }

        /**
         * Same as {@link #access(Function)} except used to unwrap {@link Optional} values returned from the provided
         * {@code chainedAccessorFunction}.
         *
         * @param chainedAccessorFunction a function which will access and unwrap nested {@link Optional} data from the
         *                                current accessor's result
         * @param <CHAINED_OUTPUT> the output type of the new chained accessor
         * @return a new chained accessor
         */
        public <CHAINED_OUTPUT> Accessor<OUTPUT, CHAINED_OUTPUT> accessAndUnwrapOptional(
                Function<OUTPUT, Optional<CHAINED_OUTPUT>> chainedAccessorFunction) {
            return new Accessor<>(this::get, getUnwrappedAccessorFunction(chainedAccessorFunction), () -> null);
        }
    }

    private static <INPUT, OUTPUT> Function<INPUT, OUTPUT> getUnwrappedAccessorFunction(
            Function<INPUT, Optional<OUTPUT>> accessorFunction) {
        return t -> accessorFunction.apply(t).orElse(null);
    }
}
