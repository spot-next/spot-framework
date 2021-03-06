package io.spotnext.support.util;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;

import io.spotnext.support.exception.UnsupportedLocale;

/**
 * <p>
 * MiscUtil class.
 * </p>
 *
 * @since 1.0
 * @author mojo2012
 * @version 1.0
 */
@SuppressWarnings({ "unchecked", "REC_CATCH_EXCEPTION" })
public class MiscUtil {

	/**
	 * Calls close() on all given objects that implement the {@link AutoCloseable} interface. Doesn't throw any exceptions at all.
	 *
	 * @param closableObjects a {@link AutoCloseable} object.
	 */
	public static void closeQuietly(final AutoCloseable... closableObjects) {
		for (final AutoCloseable c : closableObjects) {
			try {
				if (c != null) {
					c.close();
				}
			} catch (final Exception e) {
				// LOG.log(Level.WARN, String.format("An error occured while closing %s",
				// e.get));
			}
		}
	}

	/**
	 * Calls close() on all given objects that implement the {@link Closeable} interface. Doesn't throw any exceptions at all.
	 *
	 * @param closableObjects a {@link Closeable} object.
	 */
	public static void closeQuietly(final Closeable... closableObjects) {
		for (final Closeable c : closableObjects) {
			try {
				if (c != null) {
					c.close();
				}
			} catch (final IOException e) {
				// LOG.log(Level.WARN, String.format("An error occured while closing %s",
				// e.get));
			}
		}
	}

	/**
	 * <p>
	 * intOrDefault.
	 * </p>
	 *
	 * @param value        a {@link java.lang.String} object.
	 * @param defaultValue a int.
	 * @return the parsed int value, otherwise return the defaultValue.
	 */
	public static int intOrDefault(final String value, final int defaultValue) {
		if (StringUtils.isNotBlank(value)) {
			return Integer.parseInt(value);
		}

		return defaultValue;
	}

	/**
	 * @param value        the input value
	 * @param defaultValue the default value
	 * @return the prased value if it is positive, otherwise return the defaultValue.
	 */
	public static int positiveIntOrDefault(final String value, final int defaultValue) {
		if (StringUtils.isNotBlank(value)) {
			int val = Integer.parseInt(value);
			if (val >= 0) {
				return val;
			}
		}

		return defaultValue;
	}

	/**
	 * @param value        the input value
	 * @param defaultValue the default value
	 * @return the original value if it is positive, otherwise return the defaultValue.
	 */
	public static int positiveIntOrDefault(final int value, final int defaultValue) {
		if (value >= 0) {
			return value;
		}

		return defaultValue;
	}

	/**
	 * <p>
	 * longOrDefault.
	 * </p>
	 *
	 * @param value        a {@link java.lang.String} object.
	 * @param defaultValue a long.
	 * @return a long.
	 */
	public static long longOrDefault(final String value, final long defaultValue) {
		if (StringUtils.isNotBlank(value)) {
			return Long.parseLong(value);
		}

		return defaultValue;
	}

	/**
	 * <p>
	 * doubleOrDefault.
	 * </p>
	 *
	 * @param value        a {@link java.lang.String} object.
	 * @param defaultValue a double.
	 * @return a double.
	 */
	public static double doubleOrDefault(final String value, final double defaultValue) {
		if (StringUtils.isNotBlank(value)) {
			return Double.parseDouble(value);
		}

		return defaultValue;
	}

	/**
	 * <p>
	 * floatOrDefault.
	 * </p>
	 *
	 * @param value        a {@link java.lang.String} object.
	 * @param defaultValue a float.
	 * @return a float.
	 */
	public static float floatOrDefault(final String value, final float defaultValue) {
		if (StringUtils.isNotBlank(value)) {
			return Float.parseFloat(value);
		}

		return defaultValue;
	}

	/**
	 * <p>
	 * removeEnclosingQuotes.
	 * </p>
	 *
	 * @param string a {@link java.lang.String} object.
	 * @return a {@link java.lang.String} object.
	 */
	public static String removeEnclosingQuotes(final String string) {
		return string.replaceAll("^\"|\"$", "");
	}

	/**
	 * <p>
	 * toArray.
	 * </p>
	 *
	 * @param collection a {@link java.util.Collection} object.
	 * @param arrayType  a {@link java.lang.Class} object.
	 * @param            <T> a T object.
	 * @return an array of T[] objects.
	 */
	public static <T> T[] toArray(final Collection<T> collection, final Class<T> arrayType) {
		T[] ret = null;

		ret = collection.toArray((T[]) Array.newInstance(arrayType, 0));

		return ret;
	}

	/**
	 * <p>
	 * parseLocale.
	 * </p>
	 *
	 * @throws java.lang.IllegalStateException if the locale can be parsed but is not available/valid.
	 * @param localeString a {@link java.lang.String} object.
	 * @return a {@link java.util.Locale} object.
	 * @throws java.lang.IllegalStateException if any.
	 */
	public static Locale parseLocale(String localeString) throws IllegalStateException {
		Locale locale = null;

		if (StringUtils.isNotBlank(localeString)) {
			String[] splitLocaleString = localeString.split("_");

			if (splitLocaleString.length == 1) {
				locale = new Locale(localeString);
			} else if (splitLocaleString.length == 2) {
				locale = new Locale(splitLocaleString[0], splitLocaleString[1]);
			}

			if (!LocaleUtils.isAvailableLocale(locale)) {
				throw new IllegalStateException(String.format("Unknown locale %s", locale));
			}
		}

		return locale;
	}

	/**
	 * <p>
	 * getCountryLocale.
	 * </p>
	 *
	 * @param locale the locale that doesn't contain a country part, eg. for {@link java.util.Locale#ENGLISH}
	 * @return a the corresponding locale with country part, eg. {@link java.util.Locale#UK}
	 * @throws io.spotnext.support.exception.UnsupportedLocale when there is no country locale defined for the given locale.
	 * @throws io.spotnext.support.exception.UnsupportedLocale if any.
	 */
	public static Locale getCountryLocale(Locale locale) throws UnsupportedLocale {
		Locale ret = locale;
		if (StringUtils.isBlank(locale.getCountry())) {
			if (Locale.ENGLISH.equals(locale)) {
				ret = Locale.UK;
			} else if (Locale.GERMAN.equals(locale)) {
				ret = locale.GERMANY;
			} else if (Locale.FRENCH.equals(locale)) {
				ret = locale.FRANCE;
			} else if (Locale.ITALIAN.equals(locale)) {
				ret = locale.ITALY;
			} else if (Locale.CHINESE.equals(locale)) {
				ret = locale.CHINA;
			} else if (Locale.JAPANESE.equals(locale)) {
				ret = locale.JAPAN;
			} else if (Locale.KOREAN.equals(locale)) {
				ret = locale.KOREA;
			} else if (Locale.forLanguageTag("es").equals(locale)) {
				ret = new Locale(locale.getLanguage(), "ES");
			} else {
				throw new UnsupportedLocale(locale);
			}
		}

		return ret;
	}

	/**
	 * Evaluates the given expression in a null-safe way (even for nested expressions)
	 * <p>
	 * Example: $(() -&gt; order.getOrderEntries().get(0).getCode()) will never fail with a {@link java.lang.NullPointerException}!
	 * </p>
	 *
	 * @param expression the java expression to evaluate
	 * @return returns an optional of the evaluated return value
	 * @param <T> a T object.
	 */
	public static <T> Optional<T> $(Supplier<T> expression) {
		try {
			return Optional.ofNullable(expression.get());
		} catch (NullPointerException e) {
			return Optional.empty();
		}
	}

	/**
	 * Basically the same as {@link #$(Supplier)} but instead of an empty {@link java.util.Optional} it returns the given default value.
	 *
	 * @param expression   the java expression to evaluate
	 * @param defaultValue the default value to return in case of null (or an {@link java.lang.NullPointerException}
	 * @return returns the evaluated result or the default value in case it's null
	 * @param <T> a T object.
	 */
	public static <T> T $(Supplier<T> expression, T defaultValue) {
		T ret = null;

		try {
			ret = expression.get();
		} catch (NullPointerException e) {
			//
		}

		return ret != null ? ret : defaultValue;
	}

	/**
	 * Returns the given object if it is valid or the given default value if not.
	 * 
	 * @param object       to check
	 * @param check        expression
	 * @param defaultValue value to return in case the given object is not valid
	 * @return either the original object or the fallback, depending on the check
	 */
	public static <T> T validOr(T object, Function<T, Boolean> check, Supplier<T> defaultValue) {
		return check.apply(object) ? object : defaultValue.get();
	}

	public static boolean greaterThan(Number number, Number operand) {
		if (number instanceof Comparable && operand instanceof Comparable) {
			return ((Comparable<Number>) number).compareTo(operand) > 0;
		}

		throw new IllegalArgumentException("Arguments must be of type 'Comparable'");
	}

	public static <T, C extends T> void with(T object, Class<C> asCastType, Consumer<C> consumer) {
		consumer.accept((C) object);
	}
	
	public static void rethrowSilently(Exception ex) throws RuntimeException {
		if (ex instanceof RuntimeException) {
			throw (RuntimeException) ex;
		} else {
			throw new RuntimeException(ex);
		}
	}
	
	public static void tryAndThrowQuietly(ExceptionRunnable runnable, String exeptionMessage) {
		tryAndThrowQuietly(runnable, e -> {
			var msg = exeptionMessage != null ? exeptionMessage : e.getMessage();

			if (msg == null) {
				msg = String.format("Exception of type '%s' occured", e.getClass());
			}

			throw new IllegalStateException(msg);
		});
	}
	
	public static void tryAndThrowQuietly(ExceptionRunnable runnable, Consumer<Exception> exeptionHandler) {
		try {
			runnable.run();
		} catch (Exception e) {
			exeptionHandler.accept(e);
		}
	}

	public static interface ExceptionRunnable {
		void run() throws Exception;
	}
}
