/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.js.guice;

import com.google.common.util.concurrent.ListenableFuture;
import de.deverado.framework.js.api.JavascriptEngineContext;

import java.util.function.Function;

/**
 * Uses a single thread executor to execute javascript on an engine. The results are
 * communicated with futures and the tasks for the javascript engine are queued in an
 * unlimited queue.
 */
public interface JavascriptExecutor {

    /**
     * Use this to call any function on the {@link JavascriptEngineContext} within the thread.
     * @return the return value future with the result of function call.
     */
    ListenableFuture<Object> submit(Function<JavascriptEngineContext, Object> func);

    /**
     * See {@link JavascriptEngineContext#eval(String)}.
     */
    ListenableFuture<Object> submitEval(String scriptCode);

    /**
     * See {@link JavascriptEngineContext#invokeFunction(String, Object...)}.
     */
    ListenableFuture<Object> submitFunctionInvocation(String functionName, Object... jsCompatibleArgs);

    /**
     * See {@link JavascriptEngineContext#invokeMethod(Object, String, Object...)}.
     */
    ListenableFuture<Object> submitMethodInvocation(Object scriptObject, String methodName,
                                                           Object... jsCompatibleArgs);
}
