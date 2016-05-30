package com.github.rmannibucau.bootee.internal;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class TomEESpringPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        if (configurableListableBeanFactory.getBeanNamesForType(TomEEEmbeddedServletContainerFactory.class).length == 0) {
            // override tomcat one to let it work with auto config
            configurableListableBeanFactory.registerSingleton("tomcatEmbeddedServletContainerFactory", new TomEEEmbeddedServletContainerFactory(configurableListableBeanFactory));
        }
    }
}
