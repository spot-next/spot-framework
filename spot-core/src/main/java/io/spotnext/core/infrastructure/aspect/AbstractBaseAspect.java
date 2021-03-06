package io.spotnext.core.infrastructure.aspect;

import java.lang.annotation.Annotation;

import org.aspectj.lang.JoinPoint;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;

//import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.spotnext.support.util.ClassUtil;

/**
 * <p>Abstract AbstractBaseAspect class.</p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
//@Configuration
@Configurable(autowire = Autowire.BY_TYPE, dependencyCheck = true, preConstruction = true)
public abstract class AbstractBaseAspect {

	protected <A extends Annotation> A getAnnotation(final JoinPoint joinPoint, final Class<A> annotation) {
		return ClassUtil.getAnnotation(joinPoint, annotation);
	}

	protected String createSignature(final JoinPoint joinPoint) {
		return String.format("%s.%s", joinPoint.getSignature().getClass().getSimpleName(),
				joinPoint.getSignature().getName());
	}

}
