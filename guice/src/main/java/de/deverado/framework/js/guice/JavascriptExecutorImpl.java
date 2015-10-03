/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.js.guice;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Singleton;
import de.deverado.framework.js.api.JavascriptEngineContext;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Uses a single thread executor to execute javascript on an engine. The results are
 * communicated with futures and the tasks for the javascript engine are queued in an
 * unlimited queue.
 */
@Singleton
public class JavascriptExecutorImpl implements  JavascriptExecutor {

    @Inject
    @Named("javascriptExecutor")
    ListeningExecutorService executor;

    @Inject
    JavascriptEngineContext engineAndContext;

    /**
     * See {@link JavascriptEngineContext#invokeFunction(String, Object...)}.
     */
    public ListenableFuture<Object> submitFunctionInvocation(String functionName, Object... jsCompatibleArgs) {
        return executor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return engineAndContext.invokeFunction(functionName, jsCompatibleArgs);
            }
        });
    }

    /**
     * See {@link JavascriptEngineContext#invokeMethod(Object, String, Object...)}.
     */
    public ListenableFuture<Object> submitMethodInvocation(Object scriptObject, String methodName,
                                                           Object... jsCompatibleArgs) {
        return executor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return engineAndContext.invokeMethod(scriptObject, methodName, jsCompatibleArgs);
            }
        });
    }

    public ListenableFuture<Object> submitEval(String scriptCode) {
        return executor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return engineAndContext.eval(scriptCode);
            }
        });
    }

    /**
     * Use this to call any function on the {@link JavascriptEngineContext} within the thread.
     * @return the return value future with the result of function call.
     */
    public ListenableFuture<Object> submit(Function<JavascriptEngineContext, Object> func) {
        return executor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return func.apply(engineAndContext);
            }
        });
    }
}
