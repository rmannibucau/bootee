package com.github.rmannibucau.bootee;

import com.github.rmannibucau.bootee.internal.TomEEConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@Import(TomEEConfiguration.class)
public @interface EnableBootEE {
    /**
     * @return should CDI beans be registered as Spring beans.
     */
    boolean registerCdiBeansAsSpringBeans() default true;

    /**
     * @return CDI scopes to register as beans. If the CDI bean has no name one is generated.
     */
    Class<?>[] registrationScopes() default {};

    /**
     * @return the transaction manager bean name. Emtpy means no registration.
     */
    String transactionManagerName() default "";

    /**
     * @return the user transaction bean name. Emtpy means no registration.
     */
    String userTransactionName() default "";
}
