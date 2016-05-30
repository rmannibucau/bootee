package com.github.rmannibucau.bootee.internal;

import org.apache.openejb.cdi.CdiScanner;

import java.util.Iterator;

// simply removes spring-boot classes from CDI context
// TODO: make it configurable and likely remove spring ones too (but some are CDI beans, so check before it is ok)
public class BootEEScannerService extends CdiScanner {
    @Override
    public void init(final Object object) {
        super.init(object);
        final Iterator<Class<?>> it = getBeanClasses().iterator();
        while (it.hasNext()) {
            if (it.next().getName().startsWith("org.springframework.boot.")) {
                it.remove();
            }
        }
    }
}
