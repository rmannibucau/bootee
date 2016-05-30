package com.github.rmannibucau.bootee.internal;

import org.springframework.boot.context.embedded.ServletContextInitializer;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Set;

public class SpringToServletInitializer implements ServletContainerInitializer {
    private final ServletContextInitializer[] initializers;

    public SpringToServletInitializer(final ServletContextInitializer[] initializers) {
        this.initializers = initializers;
    }

    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) throws ServletException {
        if (initializers == null) {
            return;
        }
        for (final ServletContextInitializer i : initializers) {
            i.onStartup(ctx);
        }
    }
}
