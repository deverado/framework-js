package de.deverado.framework.js.nashorn;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.io.CharSource;
import de.deverado.framework.js.api.JavascriptEngineContext;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.script.ScriptContext;

import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * A convenience facade to the Nashorn script engine. This context holds functions and variables - not thread safe, but with
 * synchronization helpers.
 * If you need the last bit of performance you can of course avoid object creation and locking overhead by using
 * {@link NashornHelper}'s methods with the internal engine and context objects. But the difference is in the range
 * of one string concatenation. One (small) string concatenation in the called JS code is taking as much time as the
 * overhead introduced by this convenience class.
 *
 * @see NashornHelper for details on how Nashorn is used internally.
 */
@ParametersAreNonnullByDefault
public class NashornJavascriptEngineContext implements JavascriptEngineContext {

    private ScriptContext context;

    private NashornJavascriptEngineHolder engineHolder;

    /**
     * Engines should be created very rarely. You may reuse one engine with multiple contexts if you need to
     * separate global object contexts.
     * @return initialized context
     */
    public static NashornJavascriptEngineContext createWithNewEngine() {
        return createForExistingEngine(NashornJavascriptEngineHolder.createWithNewEngine());
    }

    /**
     * Wraps the engine into a holder and uses it for a new context.
     *
     * @param engine created with own parameters or via {@link NashornHelper} methods like
     * {@link NashornHelper#createEngine(boolean)}.
     */
    public static NashornJavascriptEngineContext createWithNewEngine(NashornScriptEngine engine) {
        return createForExistingEngine(
                NashornJavascriptEngineHolder.createForExistingEngine(engine));
    }

    public static NashornJavascriptEngineContext createForExistingEngine(
            NashornJavascriptEngineHolder engineHolder) {
        NashornJavascriptEngineContext result = new NashornJavascriptEngineContext();
        result.engineHolder = engineHolder;
        try (NashornJavascriptEngineHolder.NashornJavascriptEngine engine = engineHolder.open()) {
            result.context = NashornHelper.createContext(engine.getEngine());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public NashornJavascriptEngineHolder getEngineHolder() {
        return engineHolder;
    }

    public ScriptContext getContext() {
        return context;
    }

    public Object withEngineAndContext(Function<Pair<NashornScriptEngine, ScriptContext>, Object> function) {
        return engineHolder.withEngine(engine -> {
            return function.apply(Pair.of(engine, context));
        });
    }

    /**
     * Intended for high performance function calls (little overhead). The best method for invoking
     * functions is using the {@link javax.script.Invocable#invokeFunction(String, Object...)} method which is
     * implemented by {@link NashornScriptEngine}.
     * @param jsCompatibleArgs Maps, standard types and lists. Arrays work for iteration, access and even
     *                         assignment of fitting types.
     */
    @Override
    public Object invokeFunction(String functionName, Object... jsCompatibleArgs) {
        return engineHolder.withEngine(engine -> {
            try {
                return NashornHelper.invokeFunction(engine, context, functionName, jsCompatibleArgs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Intended for high performance method calls (little overhead). The best method for invoking
     * methods is using the {@link javax.script.Invocable#invokeMethod(Object, String, Object...)} method which is
     * implemented by {@link NashornScriptEngine}.
     * @param scriptObject obtained as return value from
     *               {@link #invokeFunction(String, Object...)},
     *               {@link #invokeMethod(Object, String, Object...)} or
     *                     {@link #getScriptObject(String)} and eval and its
     *                     siblings.
     * @param jsCompatibleArgs Maps, standard types and lists. Arrays work for iteration, access and even
     *                         assignment of fitting types.
     */
    @Override
    public Object invokeMethod(Object scriptObject, String methodName, Object... jsCompatibleArgs) {
        return engineHolder.withEngine(engine -> {
            try {
                return NashornHelper.invokeMethod(engine, context, scriptObject, methodName, jsCompatibleArgs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Slow, uses eval, an alias to eval.
     * @param scriptCodeReturningObject code like 'someObject.somePropertyName[4].anotherProperty .
     * @return object suitable for {@link #invokeMethod(Object, String, Object...)}.
     */
    @Override
    public Object getScriptObject(String scriptCodeReturningObject) {
        return engineHolder.withEngine(engine -> {
            try {
                return NashornHelper.getScriptObject(engine, context, scriptCodeReturningObject);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

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
    @Override
    public Object eval(String scriptCode) {
        return engineHolder.withEngine(engine -> {
            try {
                return NashornHelper.eval(engine, context, scriptCode);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void evalIntoEngineUTF8Resource(String resourceName, Class<?> relativeTo) {
        engineHolder.withEngine(engine -> {
            try {
                NashornHelper.evalIntoEngineUTF8Resource(engine, context, resourceName, relativeTo);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Load lib into engine to be able to call it from other scripts or via
     * {@link #invokeFunction(String, Object...)} or {@link #invokeMethod(Object, String, Object...)}.
     *
     */
    @Override
    public void evalIntoEngine(String name, String content) {
        engineHolder.withEngine(engine -> {
            try {
                NashornHelper.evalIntoEngine(engine, context, name, content);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A node-fake-compatible require() implementation useful for loading common-js style modules. After this
     * the context 'knows' require.
     * @param requireTargets read when required, module names/paths to CharSources that provide the source.
     *                       See {@link NashornHelper#addClasspathResources(LinkedHashMap, String, String...)}.
     */
    @Override
    public void loadWithRequire(LinkedHashMap<String, CharSource> requireTargets) {
        engineHolder.withEngine(engine -> {
            try {
                NashornHelper.loadWithRequire(engine, context, requireTargets);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
