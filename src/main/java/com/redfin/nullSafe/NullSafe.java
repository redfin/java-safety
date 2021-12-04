package com.redfin.nullSafe;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * This is a utility class that provides null-safe access to nested fields, similar to that implicitly supported in
 * other languages such as Swift. It's preferable to another workaround to Java's limited support for null-safe
 * access, filtering out nulls via Streams or Optionals, as this library provides:
 * <ol>
 *     <li><b>Quality:</b> The null checks are embedded within this library itself, making it more difficult to check in
 *     buggy code.</li>
 *     <li><b>Readable:</b> It's obvious what the caller is doing, as the purpose is intrinsic in this library's
 *     name and syntax.</li>
 *     <li><b>Flexible:</b> This library supports the invocation of optional in-line null/error handlers.</li>
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
 *     return NullSafe.from(getA())
 *         .access(ClassA::getB)
 *         .access(ClassB::getC)
 *         .access(ClassC::getSomeField)
 *         .get(); // or, alternatively, .toOptional()
 * </pre>
 * The library will return null if the first "root-node" data (in the case above, the result from {@code getA()}),
 * or any subsequently fetched data from the provided functions, evaluates to null. You can also specify custom null
 * handlers, or backup values directly, at any level of the chain. The latter-most null handler will catch any nulls
 * that preceded it. (The default null handler simply returns null.)
 * <pre>
 *     ClassA a = getA();
 *     return NullSafe.from(a)
 *         .ifNull(() -> { throw new IllegalArgumentException("a must be non-null"); }) // throwing if null
 *         .access(ClassA::getB)
 *         .access(ClassB::getC)
 *         .ifNull(defaultC) // providing a default value directly
 *         .access(ClassC::getSomeField)
 *         .ifNull(() -> getDefaultField()) // invoking another function to get the default
 *         .get(); // or, alternatively, .toOptional()
 * </pre>
 * Access methods and null handlers are invoked on demand—that is only once {@link #toOptional()} or {@link #get()} is
 * called.
 * <p/>
 * If you wish to "flatten" (i.e. evaluate) the chained accessors before returning a {@code NullSafe} instance
 * from the current method's scope, potentially for further chaining outside the given method without retaining
 * references to unnecessary data, use the {@link #evaluate()} method.
 */
public class NullSafe<T> {

    private final Supplier<T> dataSupplier;
    private Supplier<T> nullHandler;
    private Boolean isTrulyNullSafe;

    //region public constructors

    /**
     * Construct a {@link NullSafe} instance from some source data.
     *
     * @param data     the source data from which nested data will be accessed
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
     * @param <SOURCE>     the type of source data
     * @return a new {@link NullSafe} instance
     */
    public static <SOURCE> NullSafe<SOURCE> from(Optional<? extends SOURCE> dataOptional) {
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
     * <p/>
     * Only one null handler is allowed per {@link NullSafe} instance. Invoking this method "back to back" on the same
     * {@link NullSafe} instance will overwrite any preexisting null handler:
     * <pre>
     *     NullSafe.from(data)
     *             .access(ClassA::method1) // suppose method1 returns null
     *             .ifNull(() -> nullHandler1) // nullHandler1 will NOT be called
     *             .ifNull(() -> nullHandler2) // nullHandler2 will be called
     *             .get();
     * </pre>
     *
     * @param nullHandler a supplier for fall-back data (if desired) that is invoked if this the data provided by this
     *                    {@link NullSafe} instance is null
     * @return this {@link NullSafe} instance
     */
    public NullSafe<T> ifNull(Supplier<T> nullHandler) {
        this.nullHandler = nullHandler;
        isTrulyNullSafe = null;
        return this;
    }

    /**
     * Version of {@link #ifNull(Supplier)} that allows you to provide a backup value directly rather than a
     * {@link Supplier} function to be called in the case of null data.
     *
     * @param backupValue a value to be provided in the case of null data
     * @return this {@link NullSafe} instance
     */
    public NullSafe<T> ifNull(T backupValue) {
        this.nullHandler = () -> backupValue;
        isTrulyNullSafe = null;
        return this;
    }

    /**
     * Access nested data provided by the given {@code accessorFunction}; the data contained within the
     * {@link NullSafe} instance will be applied to the provided function.
     *
     * @param accessorFunction a function to access nested data
     * @param <OUTPUT>         the output type of the given function
     * @return a new {@link NullSafe} instance
     */
    public <OUTPUT> NullSafe<OUTPUT> access(Function<? super T, ? extends OUTPUT> accessorFunction) {
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
     *                         {@code OUTPUT}
     * @param <OUTPUT>         the output type of the given function, after the {@link Optional} is unwrapped
     * @return a new {@link NullSafe} instance
     */
    public <OUTPUT> NullSafe<OUTPUT> accessAndUnwrapOptional(Function<? super T, Optional<? extends OUTPUT>> accessorFunction) {
        return new NullSafe<>(() -> {
            T data = get();
            if (data == null) {
                return null;
            }
            return accessorFunction.apply(data).orElse(null);
        });
    }

    /**
     * Get the nested data provided by this {@link NullSafe} instance; note that {@link #toOptional()} should be
     * preferred, in the spirit of avoiding the proliferation of null values.
     *
     * @return the nested data from this {@link NullSafe} instance
     * @see #toOptional()
     */
    public T get() {
        T data = dataSupplier.get();
        if (data == null) {
            return nullHandler.get();
        }
        return data;
    }

    /**
     * Get the nested data provided by this {@link NullSafe} instance; the preferred terminal operation over
     * {@link #get}, in the spirit of avoiding the proliferation of null values.
     *
     * @return the nested data from this {@link NullSafe} instance, wrapped in a nullable {@link Optional}
     * @see #get
     */
    public Optional<T> toOptional() {
        return Optional.ofNullable(get());
    }

    /**
     * Get the nested data provided by this {@link NullSafe} instance and converts into a {@link Stream}.
     *
     * @return {@link Stream} of the nested data, if null then returns an empty {@link Stream}.
     */
    public Stream<T> stream() {
        T data = get();
        if (data == null) {
            return Stream.empty();
        }
        return Stream.of(data);
    }

    /**
     * Checks if {@link #get()} returns non-null value.
     *
     * @apiNote The value is cache to minimize performance of repeated use.
     * @return {@code true} if {@link #get()} returns non-null value, otherwise {@code false}.
     */
    public boolean isNonNull() {
        if (isTrulyNullSafe == null) {
            isTrulyNullSafe = get() != null;
        }
        return isTrulyNullSafe;
    }

    /**
     * Evaluate the chained accessor methods underlying this {@link NullSafe} instance and create a new
     * {@link NullSafe} instance from the resulting data. <b>Use this method if you want to return a {@link NullSafe}
     * instance from a method but want to ensure that any nested data up until this point has been fully accessed for
     * safety, monitoring, or performance reasons.</b> Example:
     * <pre>{@code
     *     // Get an instance with a reference held to someObjectA
     *     NullSafe<ClassB> nullSafeInstance = NullSafe.of(someObjectA)
     *                                                 .access(ClassA::getB);
     *
     *     // Get an instance with NO reference held to someObjectA
     *     NullSafe<ClassB> newNullSafeInstance = NullSafe.of(someObjectA)
     *                                                    .access(ClassA::getB)
     *                                                    .evaluate();
     * }</pre>
     * <p/>
     * <h2>Example: Crossing a Database Transaction Boundary</h2>
     * Suppose you are accessing data that incurs a latency cost that you want to measure (e.g. over the network) or
     * that can only be accessed within the current scope (e.g. within a database transaction) but for which you still
     * want to access more nested data after the given operations:
     * <pre>{@code
     *     @WithinDatabaseTransaction
     *     NullSafe<Person> getPerson(long accountId) {
     *         Account account = dataService.get(accountId);
     *
     *         // account is a proxy object to the database,
     *         // and the proxy will be resolved when this method returns
     *
     *         return NullSafe.from(account)
     *                        .access(Account::getPerson);
     *     }
     *
     *     public String getValidEmailAddress(long accountId) {
     *         NullSafe<Person> person = getPerson(accountId);
     *
     *         // At this point, person is a NullSafe instance with a nested data
     *         // supplier--not yet invoked--of the following form:
     *         //
     *         //    () -> {
     *         //        return account.getPerson();
     *         //    }
     *         //
     *         // meaning that getPerson has not yet been invoked.
     *
     *         return person.ifNull(() -> {
     *                         throw new IllegalStateException(
     *                             "no Person associated with the given account ID");
     *                       })
     *                      .access(p -> {
     *                          String emailAddress = p.getEmailAddress();
     *                          validateEmailAddressOrThrow(emailAddress);
     *                          return emailAddress;
     *                      })
     *                      .get();
     *     }
     * }</pre>
     * Under normal circumstances, {@code account}, {@code person}, and {@code emailAddress} would not be fetched until
     * {@link #get} is invoked in the {@code getValidEmailAddress} method. However, by that point, the method
     * interceptor for {@code @WithinDatabaseTransaction} will have already closed the required database transaction,
     * making that data inaccessible. To mitigate this issue, rather than having to return a raw {@code Person} from
     * {@code getPerson}, we can use {@code evaluate} as follows:
     * <pre>{@code
     *     @WithinDatabaseTransaction
     *     NullSafe<Person> getPerson(long accountId) {
     *         Account account = dataService.get(accountId);
     *
     *         // account is a proxy object to the database,
     *         // and the proxy will be resolved when this method returns
     *
     *         return NullSafe.from(account)
     *                        .access(Account::getPerson)
     *                        .evaluate(); // *** INVOKES getPerson BEFORE RETURNING ***
     *     }
     *
     *     public String getValidEmailAddress(long accountId) {
     *         NullSafe<Person> person = getPerson(accountId);
     *
     *         // person is now a "flattened" NullSafe instance with a single data
     *         // supplier of the form:
     *         //
     *         //    () -> evaluatedPersonData
     *         //
     *         // meaning that getPerson has been invoked.
     *
     *         return person.ifNull(() -> {
     *                         throw new IllegalStateException(
     *                             "no Person associated with the given account ID");
     *                       })
     *                      .access(p -> {
     *                          String emailAddress = p.getEmailAddress();
     *                          validateEmailAddressOrThrow(emailAddress);
     *                          return emailAddress;
     *                      })
     *                      .get();
     *     }
     * }</pre>
     *
     * @return a new {@link NullSafe} instance created with the fully evaluated data provided by this
     * {@link NullSafe} instance
     */
    public NullSafe<T> evaluate() {
        T data = get();
        return new NullSafe<>(() -> data);
    }

    //endregion
}
