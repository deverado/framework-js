/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.js.guice;

import com.google.inject.AbstractModule;
import de.deverado.framework.guice.ThreadPoolHelpers;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class JavascriptExecutorModule extends AbstractModule {

    @Override
    protected void configure() {
        ThreadPoolHelpers.bindControllableExecutor("javascriptExecutor", binder(),
                1, 1, -1, 50000, -1);
        bind(JavascriptExecutor.class).to(JavascriptExecutorImpl.class);
    }
}
