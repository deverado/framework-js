/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.js.guice;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import de.deverado.framework.js.api.JavascriptEngineContext;
import de.deverado.framework.js.nashorn.NashornJavascriptEngineContext;
import de.deverado.framework.js.nashorn.NashornJavascriptEngineHolder;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This class manages engines that are intended to live for a long time and be reused if javascript
 * is invoked a couple of times during the runtime of the program. The Engines are not thread safe
 * but the classes returned by this engine <b>enforce synchronization</b>. That might lead to
 * deadlock problems. If for some reason (e.g. throughput) multiple threads are required to execute Javascript
 * code in parallel then multiple module instances should be created differentiated by name with
 * the {@link #create(String)} method and siblings. Be aware that at the time of writing the Nashorn
 * JS implementation is very sensitive to warmup and requires a hundred iterations to optimize
 * complex code and during that optimization phase uses multiple cores affecting throughput. Multiple
 * engines each need to optimize the code by themselves. So while in the long run throughput might be
 * affected positively by having multiple engines run code, the warmup phase might get a lot slower.
 * This stands against the expected speedup of multiple threads.
 */
@ParametersAreNonnullByDefault
public class NashornJavascriptModule extends AbstractModule {

    private static NashornJavascriptModule result;
    private NashornJavascriptEngineContext engineAndContext;
    private String name;

    public static  NashornJavascriptModule create() {
        return new NashornJavascriptModule();
    }

    public static  NashornJavascriptModule create(String name) {
        result = new NashornJavascriptModule();
        result.name = name;
        return result;
    }

    public static  NashornJavascriptModule createWithEngine(NashornJavascriptEngineHolder engineHolder,
                                                            @Nullable String bindingName) {
        NashornJavascriptModule result = new NashornJavascriptModule();
        result.engineAndContext = NashornJavascriptEngineContext.createForExistingEngine(engineHolder);
        result.name = bindingName;
        return result;
    }

    public static  NashornJavascriptModule createWithContext(NashornJavascriptEngineContext engineAndContext,
                                                             @Nullable String bindingName) {
        NashornJavascriptModule result = new NashornJavascriptModule();
        result.engineAndContext = engineAndContext;
        result.name = bindingName;
        return result;
    }

    @Override
    protected void configure() {
        if (engineAndContext == null) {
            engineAndContext = NashornJavascriptEngineContext.createWithNewEngine();
        }
        if (StringUtils.isNotBlank(name)) {
            bind(JavascriptEngineContext.class).annotatedWith(Names.named(name)).toInstance(engineAndContext);
        } else {
            bind(JavascriptEngineContext.class).toInstance(engineAndContext);
        }
    }

    public String getName() {
        return name;
    }

    public NashornJavascriptEngineContext getEngineAndContext() {
        return engineAndContext;
    }
}
