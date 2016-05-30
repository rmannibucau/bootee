package com.github.rmannibucau.bootee;

import org.apache.openejb.OpenEJB;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.transaction.TransactionManager;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BootEETest {
    public static final AtomicReference<CountDownLatch> STARTED = new AtomicReference<>();
    public static final AtomicReference<CountDownLatch> STOPPED = new AtomicReference<>();

    @Test
    public void run() throws InterruptedException {
        STARTED.set(new CountDownLatch(1));
        STOPPED.set(new CountDownLatch(1));
        final ConfigurableApplicationContext ctx = new SpringApplication(BootEEApp.class).run();
        assertTrue(STARTED.get().await(1, TimeUnit.MINUTES));
        final Map<String, TransactionManager> txMgrs = ctx.getBeansOfType(TransactionManager.class);
        assertTrue(txMgrs.containsKey("txMgr"));
        assertEquals(txMgrs.get("txMgr"), OpenEJB.getTransactionManager());
        ctx.close();
        assertTrue(STOPPED.get().await(1, TimeUnit.MINUTES));
    }

    @SpringBootApplication
    @EnableAutoConfiguration
    @EnableBootEE(registrationScopes = ApplicationScoped.class, transactionManagerName = "txMgr")
    public static class BootEEApp {
        @Bean
        @Lazy // to get cdi beans since they are added once tomee is started
        protected ServletContextListener listener() {
            return new ServletContextListener() {
                @Inject
                private Service service; // from CDI

                @Override
                public void contextInitialized(final ServletContextEvent sce) {
                    assertTrue(service.getClass().getName().contains("OwbNormalScopeProxy")); // ensure it comes from CDI
                    service.countDown(STARTED);
                }

                @Override
                public void contextDestroyed(final ServletContextEvent sce) {
                    service.countDown(STOPPED);
                }
            };
        }
    }

    @ApplicationScoped
    public static class Service {
        public void countDown(final AtomicReference<CountDownLatch> latchAtomicReference) {
            latchAtomicReference.get().countDown();
        }
    }
}
