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
 * Access methods and null handlers are invoked on demand—that is only once {@link #get()} is called.
 */
public class NullSafe<T> {

    private final Supplier<T> dataSupplier;
    private Supplier<T> nullHandler;

    //region public constructors

    /**
     * Construct a {@link NullSafe} instance from some source data.
     *
     * @param data the source data from which nested data will be accessed
     * @param <SOURCE> the type of source data
     * @return a new {@link NullSafe} instance
     */
    public static <SOURCE> NullSafe<SOURCE> from(SOURCE data) {
        return new NullSafe<>(() -> data);
    }

    /**
     * Construct a {@link NullSafe} instance from some {@link Optional} source data. The {@link Optional} will be
     * unwrapped at the time this method is invoked.
     *
     * @param dataOptional the {@link Optional} source data from which nested data will be accessed
     * @param <SOURCE> the type of source data
     * @return a new {@link NullSafe} instance
     */
    public static <SOURCE> NullSafe<SOURCE> from(Optional<SOURCE> dataOptional) {
        return new NullSafe<>(() -> dataOptional.orElse(null));
    }

    //endregion

    // private constructor to prevent instantiation
    private NullSafe(Supplier<T> dataSupplier) {
        this.dataSupplier = dataSupplier;
        this.nullHandler = () -> null;
    }

    //region public methods

    /**
     * Set the null handler for this {@link NullSafe} instance such that if the accessed data is null,
     * rather than returning null (the default behavior), the null handler will be invoked, and its return value will be
     * returned instead.
     *
     * @param nullHandler a supplier for fall-back data (if desired) that is invoked if this the data provided by this
     *                   {@link NullSafe} instance is null
     * @return this {@link NullSafe} instance
     */
    public NullSafe<T> ifNull(Supplier<T> nullHandler) {
        this.nullHandler = nullHandler;
        return this;
    }

    /**
     * Access nested data provided by the given {@code accessorFunction}; the data contained within the
     * {@link NullSafe} instance will be applied to the provided function.
     *
     * @param accessorFunction a function to access nested data
     * @param <OUTPUT> the output type of the given function
     * @return a new {@link NullSafe} instance
     */
    public <OUTPUT> NullSafe<OUTPUT> access(Function<T, OUTPUT> accessorFunction) {
        return new NullSafe<>(() -> {
            T data = get();
            if (data == null) {
                return null;
            }
            return accessorFunction.apply(data);
        });
    }

    /**
     * Same as {@link #access(Function)} except used to unwrap {@link Optional} values returned from the provided
     * {@code accessorFunction}.
     *
     * @param accessorFunction a function to access nested data, expected to return an {@link Optional} of type
     * {@code OUTPUT}
     * @param <OUTPUT> the output type of the given function, after the {@link Optional} is unwrapped
     * @return a new {@link NullSafe} instance
     */
    public <OUTPUT> NullSafe<OUTPUT> accessAndUnwrapOptional(Function<T, Optional<OUTPUT>> accessorFunction) {
        return new NullSafe<>(() -> {
            T data = get();
            if (data == null) {
                return null;
            }
            return accessorFunction.apply(data).orElse(null);
        });
    }

    /**
     * @return the nested data from this {@link NullSafe} instance
     */
    public T get() {
        T data = dataSupplier.get();
        if (data == null) {
            return nullHandler.get();
        }
        return data;
    }

    /**
     * @return the nested data from this {@link NullSafe} instance, wrapped in a nullable {@link Optional}
     */
    public Optional<T> toOptional() {
        return Optional.ofNullable(get());
    }

    //endregion
}
