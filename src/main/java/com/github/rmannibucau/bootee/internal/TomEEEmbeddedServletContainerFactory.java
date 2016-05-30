package com.github.rmannibucau.bootee.internal;

import com.github.rmannibucau.bootee.EnableBootEE;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.StandardContext;
import org.apache.openejb.OpenEJB;
import org.apache.openejb.cdi.WebBeansContextCreated;
import org.apache.openejb.core.CoreUserTransaction;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.observer.Observes;
import org.apache.tomee.embedded.Configuration;
import org.apache.tomee.embedded.Container;
import org.apache.webbeans.component.BuiltInOwbBean;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.spi.ScannerService;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.embedded.AbstractEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerException;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import javax.enterprise.inject.spi.Bean;
import javax.servlet.ServletContainerInitializer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;

public class TomEEEmbeddedServletContainerFactory extends AbstractEmbeddedServletContainerFactory {
    private static final String TOMEE_CONTAINER_CONFIG_PREFIX = "tomee.container.";

    private final ConfigurableListableBeanFactory factory;

    public TomEEEmbeddedServletContainerFactory(final ConfigurableListableBeanFactory factory) {
        this.factory = factory;
    }

    @Override
    public EmbeddedServletContainer getEmbeddedServletContainer(final ServletContextInitializer... initializers) {
        final Configuration configuration = new Configuration();

        configuration.setProperties(new Properties());
        configuration.getProperties().put(
                "openejb.additional.exclude",
                "spring-boot,spring-aop,spring-beans,spring-context,spring-core,spring-expression,spring-web");

        final Environment environment = factory.getBean(Environment.class);
        StreamSupport.stream(AbstractEnvironment.class.cast(environment).getPropertySources().spliterator(), false)
                .filter(MapPropertySource.class::isInstance)
                .forEach(s -> Stream.of(MapPropertySource.class.cast(s).getPropertyNames())
                        .filter(k -> k.startsWith(TOMEE_CONTAINER_CONFIG_PREFIX))
                        .forEach(k -> configuration.getProperties().put(k.substring(TOMEE_CONTAINER_CONFIG_PREFIX.length()), s.getProperty(k))));

        configuration.setHttpPort(getPort());
        ofNullable(getAddress()).ifPresent(a -> configuration.setHost(a.getHostName()));
        ofNullable(getSsl()).ifPresent(ssl -> {
            configuration.setSkipHttp(true);
            configuration.setKeyAlias(ssl.getKeyAlias());
            configuration.setKeystoreFile(ssl.getKeyStore());
            configuration.setKeystorePass(ssl.getKeyStorePassword());
            configuration.setKeystoreType(ssl.getKeyStoreType());
            configuration.setClientAuth(ssl.getClientAuth().name().toLowerCase(Locale.ENGLISH));
            configuration.setSslProtocol(ssl.getProtocol());
        });

        final Container container = new Container();
        container.setup(configuration);
        return new EmbeddedServletContainer() {
            @Override
            public void start() throws EmbeddedServletContainerException {
                try {
                    container.start(); // boot the container "empty"
                    // customize services to ensure spring-boot doesn't break the startup
                    SystemInstance.get().addObserver(new OWBCustomizer());
                    SystemInstance.get().addObserver(new Customizer(TomEEEmbeddedServletContainerFactory.this, factory, new SpringToServletInitializer(initializers)));
                    // deploy the classpath
                    container.deployClasspathAsWebApp(getContextPath(), getDocumentRoot());
                } catch (final Exception e) {
                    throw new EmbeddedServletContainerException(e.getMessage(), e);
                }
            }

            @Override
            public void stop() throws EmbeddedServletContainerException {
                try {
                    container.stop();
                } catch (final Exception e) {
                    throw new EmbeddedServletContainerException(e.getMessage(), e);
                }
            }

            @Override
            public int getPort() {
                return configuration.isSkipHttp() ? configuration.getHttpsPort() : configuration.getHttpPort();
            }
        };
    }

    public static final class Customizer {
        private final TomEEEmbeddedServletContainerFactory config;
        private final ServletContainerInitializer[] initializers;
        private final ConfigurableListableBeanFactory factory;
        private volatile StandardContext context;

        private Customizer(final TomEEEmbeddedServletContainerFactory config, final ConfigurableListableBeanFactory factory,
                           final ServletContainerInitializer... initializers) {
            this.config = config;
            this.factory = factory;
            this.initializers = initializers;
        }

        public void customize(@Observes final LifecycleEvent event) {
            final Object data = event.getSource();
            if (!StandardContext.class.isInstance(data)) {
                return;
            } else if (context == null) {
                context = StandardContext.class.cast(data);
                if (!(config.getContextPath().equals(context.getPath()) || ("/" + config.getContextPath()).equals(context.getPath()))) {
                    context = null;
                }
            }

            switch (event.getType()) {
                case Lifecycle.BEFORE_START_EVENT:
                    handleConfiguration();
                    customize(context);
                    break;
                default:
            }
        }

        private void handleConfiguration() {
            final ClassLoader loader = context.getLoader().getClassLoader();
            final Thread t = Thread.currentThread();
            final ClassLoader tccl = t.getContextClassLoader();
            t.setContextClassLoader(loader);
            try {
                final Collection<EnableBootEE> configs = factory.getBeansWithAnnotation(EnableBootEE.class).values().stream()
                        .map(o -> ofNullable(o.getClass().getAnnotation(EnableBootEE.class))
                                .orElseGet(() -> o.getClass().getSuperclass().getAnnotation(EnableBootEE.class))) // cglib proxies
                        .collect(Collectors.toList());
                if (configs.stream().filter(EnableBootEE::registerCdiBeansAsSpringBeans).findAny().isPresent()) {
                    final Set<Class<?>> scopes = configs.stream().map(EnableBootEE::registrationScopes).flatMap(Stream::of).collect(toSet());
                    final WebBeansContext ctx = WebBeansContext.currentInstance();
                    final BeanManagerImpl beanManager = ctx.getBeanManagerImpl();
                    for (final Bean<?> bean : beanManager.getBeans()) {
                        if (BuiltInOwbBean.class.isInstance(bean) || bean.getBeanClass() != null && bean.getBeanClass().getName().startsWith("org.springframework.")) {
                            continue;
                        }
                        if (!scopes.contains(bean.getScope())) {
                            continue;
                        }

                        final String name = ofNullable(bean.getName()).orElseGet(() -> "tomee_" + bean.toString());
                        final Object instance = beanManager.getReference(bean, bean.getBeanClass(), null); // handling only app scopes we don't care about context
                        factory.registerSingleton(name, instance);
                    }
                }
                final Optional<String> txMgr = configs.stream().map(EnableBootEE::transactionManagerName).findAny();
                if (txMgr.isPresent()) {
                    factory.registerSingleton(txMgr.get(), OpenEJB.getTransactionManager());
                }
                final Optional<String> ut = configs.stream().map(EnableBootEE::userTransactionName).findAny();
                if (ut.isPresent()) {
                    factory.registerSingleton(ut.get(), new CoreUserTransaction(OpenEJB.getTransactionManager()));
                }
            } finally {
                t.setContextClassLoader(tccl);
            }

        }

        private void customize(final StandardContext ctx) {
            config.getMimeMappings().forEach(m -> ctx.addMimeMapping(m.getExtension(), m.getMimeType()));
            config.getErrorPages().stream().forEach(e -> {
                final org.apache.tomcat.util.descriptor.web.ErrorPage tomcatEP = new org.apache.tomcat.util.descriptor.web.ErrorPage();
                tomcatEP.setErrorCode(e.getStatusCode());
                tomcatEP.setExceptionType(e.getExceptionName());
                tomcatEP.setLocation(e.getPath());
                ctx.addErrorPage(tomcatEP);
            });
            ofNullable(initializers).ifPresent(i -> Stream.of(initializers).forEach(c -> ctx.addServletContainerInitializer(c, emptySet())));
        }
    }

    public static class OWBCustomizer {
        private OWBCustomizer() {
            // no-op
        }

        public void customize(@Observes final WebBeansContextCreated owb) {
            owb.getContext().registerService(ScannerService.class, new BootEEScannerService());
        }
    }
}
