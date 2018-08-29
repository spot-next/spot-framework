package io.spotnext.core.infrastructure.annotation.logging;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.spotnext.core.infrastructure.support.LogLevel;

/**
 * <p>
 * Log class.
 * </p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
@Target({ FIELD, METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
	/**
	 * @return true if the begin of a method call should be logger
	 */
	boolean before() default true;

	/**
	 * @return true if the end of a method call should be logger
	 */
	boolean after() default false;

	/**
	 * @return true if the time for a method execution should be measured.
	 */
	boolean measureTime() default false;

	/**
	 * @return the log level for the log message.
	 */
	LogLevel logLevel() default LogLevel.INFO;

	/**
	 * Logs the given message. The following placeholders are supported:
	 * <ul>
	 * <li>classSimpleName</li>
	 * <li>className</li>
	 * <li>timestamp</li>
	 * </ul>
	 * 
	 * Example: "This is a logging message from classs $className"
	 * 
	 * @return the log message
	 */
	String message() default "";

	/**
	 * @return the message arguments.
	 */
	String[] messageArguments() default {};
}
