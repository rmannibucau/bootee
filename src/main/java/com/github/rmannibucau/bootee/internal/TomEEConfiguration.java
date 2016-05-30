package com.github.rmannibucau.bootee.internal;

import org.apache.tomee.embedded.Container;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Container.class)
public class TomEEConfiguration {
    @Bean
    public TomEESpringPostProcessor processor() {
        return new TomEESpringPostProcessor();
    }
}
