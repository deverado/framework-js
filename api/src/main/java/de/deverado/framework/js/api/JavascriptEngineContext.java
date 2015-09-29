package de.deverado.framework.js.api;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.io.CharSource;

import java.util.LinkedHashMap;

public interface JavascriptEngineContext {

    /**
     * Should provide much better performance than eval.
     */
    Object invokeFunction(String functionName, Object... jsCompatibleArgs);

    /**
     * Should provide much better performance than eval.
     * @param scriptObject obtained as return value from
     *               {@link #invokeFunction(String, Object...)},
     *               {@link #invokeMethod(Object, String, Object...)} or
     *                     {@link #getScriptObject(String)} and eval and its
     *                     siblings.
     * @param jsCompatibleArgs Maps, standard types and lists. Arrays work for iteration, access and even
     *                         assignment of fitting types.
     */
    Object invokeMethod(Object scriptObject, String methodName, Object... jsCompatibleArgs);

    /**
     * Slow, uses eval, an alias to eval.
     * @param scriptCodeReturningObject code like 'someObject.somePropertyName[4].anotherProperty .
     * @return object suitable for {@link #invokeMethod(Object, String, Object...)}.
     */
    Object getScriptObject(String scriptCodeReturningObject);

    /**
     * Run a script which should not have effect on the engine's global script variable assignments. It doesn't
     * completely shield the global content though. The highest performance can at this point only be attained by
     * using {@link #invokeFunction(String, Object...)} and
     * {@link #invokeMethod(Object, String, Object...)}. Consider using them for
     * performance critical code.
     * <p>
     *     Newly created global variables are discarded after the eval finishes. See evalIntoEngine for loading
     *     libs to reuse into the engine.
     * </p>
     * @param scriptCode to eval.
     * @return result of script evaluation
     */
    Object eval(String scriptCode);

    void evalIntoEngineUTF8Resource(String resourceName, Class<?> relativeTo);

    /**
     * Load lib into engine to be able to call it from other scripts or via
     * {@link #invokeFunction(String, Object...)} or {@link #invokeMethod(Object, String, Object...)}.
     *
     */
    void evalIntoEngine(String name, String content);

    /**
     * A node-fake-compatible require() implementation useful for loading common-js style modules. After this
     * the context 'knows' require.
     * @param requireTargets read when required, module names/paths to CharSources that provide the source.
     *                       See NashornHelper#addClasspathResources(LinkedHashMap, String, String...).
     */
    void loadWithRequire(LinkedHashMap<String, CharSource> requireTargets);
}
