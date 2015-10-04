package de.deverado.framework.js.nashorn;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Some definitions and specs to understand this class:
 * <ul>
 *     <li>Beware that some unexpected conversions can happen, such as integer computations returning double results
 *     to java.</li>
 *     <li>This class allows you to use different ScriptContexts if you need separate environments - but they
 *     belong to an engine. Don't create ScriptBindings - let this class manage them.</li>
 *     <li>If you want performance don't use eval. Instead first use
 *     {@link #evalIntoEngine(NashornScriptEngine, ScriptContext, String, String)} and its siblings and then for
 *     repetitive use call functions via {@link #invokeFunction(NashornScriptEngine, ScriptContext, String, Object...)}.
 *     eval is 15 to 100 times slower - after warmup (meaning the evaled content has been evaled thousands of times)!
 *     </li>
 *     <li>Use {@link #evalIntoEngine(NashornScriptEngine, ScriptContext, String, String)} to load for example jQuery
 *     once, or a templating engine's code, or your configuration variables.</li>
 *     <li>Call jquery or the template engine's functions and methods with
 *     {@link #invokeFunction(NashornScriptEngine, ScriptContext, String, Object...)} or
 *     {@link #invokeMethod(NashornScriptEngine, ScriptContext, Object, String, Object...)}.</li>
 *     <li>If you need to execute scripts via eval then the eval functions shield the context from
 *     accumulating being polluted by accidental assinging to global vars. BUT objects in global vars can be
 *     changed (see NashornHelperTest class).</li>
 *     <li>Internally the ENGINE_SCOPE is used to store variables and function code in the engine for multiple
 *     executions. {@link #evalIntoEngine(NashornScriptEngine, ScriptContext, String, String)} loads code into
 *     the ENGINE_SCOPE. But for eval executions the ENGINE_SCOPE is switched to GLOBAL_SCOPE and a throw-away bindings
 *     object is assigned as ENGINE_SCOPE.</li>
 * </ul>
 * <p>
 * Downside of Nashorn: Slow first 4 times of execution of a script, then catching up and overtaking Rhino.
 * Benefits of Nashorn: Fast if code is repeatedly executed, very slow first 2 times, then catching
 * up with Rhino (https://bugs.openjdk.java.net/browse/JDK-8019254). Can directly call into
 * Java objects and use them internally.
 * </p>
 * <p>
 *     Use few Engines - one for each processor max. Each requires warmup, so maybe even less than that.
 *     It's the weirdest stuff to see how nashorn handles scopes
 *     (see https://wiki.openjdk.java.net/display/Nashorn/Nashorn+jsr223+engine+notes for difficult explanation).
 *     Sometimes nashorn mostly write into the nashorn.global object in the ENGINE_CONTEXT on new
 *     assignments (if not using bindings created with this class's {@link #createContext(NashornScriptEngine)}
 *     at the moment, see there and in tests for details). In that case
 *     Content put into Bindings gets read, but shadowed directly on write.
 * </p>
 * <p>
 *     Officially an ENGINE_SCOPE bindings is intended for two-way communication (see a lot of explanation in
 *     this (old) email: http://mail.openjdk.java.net/pipermail/nashorn-dev/2013-December/002565.html).
 *     BUT: How engine scope is used depends greatly on how it was created: If it was created with
 *     engine.createBindings() it might come as a ScriptObjectMirror without nashorn.global and allow simple two-way
 *     comms. If you use the default created by new ScriptContext() different things might happen.
 * </p>
 */
@ParametersAreNonnullByDefault
public class NashornHelper {

    /**
     * Engine is not thread safe.
     */
    public static NashornScriptEngine createEngine() {
        return (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    }

    /**
     * Context (scope) stays associated with engine and therefore isn't thread safe.
     */
    public static ScriptContext createContext(NashornScriptEngine engine) {

        ScriptContext result = new SimpleScriptContext();
        result.setBindings(engine.createBindings(), ScriptContext.GLOBAL_SCOPE);
        result.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

        return result;
    }

    public static NashornScriptEngine createEngine(boolean optimisticTyping) {
        return (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine("-ot=" + optimisticTyping);
    }

    public static NashornScriptEngine createEngineWithPersistentCodeCache(boolean optimisticTyping) {
        return (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine("-pcc", "-ot=" + optimisticTyping);
    }

    /**
     * Compiled script belongs to engine - will always execute in same engine: So this is synonymous
     * to {@link #evalIntoEngine(NashornScriptEngine, ScriptContext, String, String)}.
     */
    public static void compile(NashornScriptEngine nashorn, ScriptContext context, String name, String script) throws Exception {
        evalIntoEngine(nashorn, context, name, script);
    }

    /**
     * Intended for high performance function calls (little overhead). The best method for invoking
     * functions is using the {@link javax.script.Invocable#invokeFunction(String, Object...)} method which is
     * implemented by {@link NashornScriptEngine}.
     * @param engine MODIFIED, if given context is not set as engine's context it is assigned before execution. As it
     *               is costly to change the context the context is left assuming that it might be reused.
     * @param jsCompatibleArgs Maps, standard types and lists. Arrays work for iteration, access and even
     *                         assignment of fitting types
     */
    public static Object invokeFunction(NashornScriptEngine engine, ScriptContext context,
                                        String functionName, Object... jsCompatibleArgs) throws Exception {
        if (engine.getContext() != context) {
            engine.setContext(context);
        }
        return engine.invokeFunction(functionName, jsCompatibleArgs);
    }

    /**
     * Intended for high performance method calls (little overhead). The best method for invoking
     * methods is using the {@link javax.script.Invocable#invokeMethod(Object, String, Object...)} method which is
     * implemented by {@link NashornScriptEngine}.
     * @param engine MODIFIED, if given context is not set as engine's context it is assigned before execution. As it
     *               is costly to change the context the context is left assuming that it might be reused.
     * @param scriptObject obtained as return value from
     *               {@link #invokeFunction(NashornScriptEngine, ScriptContext, String, Object...)},
     *               {@link #invokeMethod(NashornScriptEngine, ScriptContext, Object, String, Object...)} or
     *                     {@link #getScriptObject(NashornScriptEngine, ScriptContext, String)} and eval and its
     *                     siblings.
     * @param jsCompatibleArgs Maps, standard types and lists. Arrays work for iteration, access and even
     *                         assignment of fitting types.
     */
    public static Object invokeMethod(NashornScriptEngine engine, ScriptContext context,
                                      Object scriptObject, String MethodName, Object... jsCompatibleArgs) throws Exception {
        if (engine.getContext() != context) {
            engine.setContext(context);
        }
        return engine.invokeMethod(scriptObject, MethodName, jsCompatibleArgs);
    }

    /**
     * Slow, uses eval, an alias to eval.
     * @param scriptCodeReturningObject code like 'someObject.somePropertyName[4].anotherProperty .
     * @return object suitable for {@link #invokeMethod(NashornScriptEngine, ScriptContext, Object, String, Object...)}.
     */
    public static Object getScriptObject(NashornScriptEngine engine, ScriptContext context,
                                   String scriptCodeReturningObject) throws Exception {
        return eval(engine, context, scriptCodeReturningObject);
    }

    /**
     * Run a script which should not have effect on the engine's global script variable assignments. It doesn't
     * completely shield the global content though. The highest performance can at this point only be attained by
     * using {@link #invokeFunction(NashornScriptEngine, ScriptContext, String, Object...)} and
     * {@link #invokeMethod(NashornScriptEngine, ScriptContext, Object, String, Object...)}. Consider using them for
     * performance critical code.
     * <p>
     *     You can provide a scope here, but mind that with nashorn code stays deeply associated with engine objects:
     * Warmup for example is currently required for each engine separately.
     * </p>
     * @param engine created with this class
     * @param context created with this class. Scope bindings are switched to provide shielding of global content.
     * @param scriptCode to eval.
     * @return result of script evaluation
     */
    public static Object eval(NashornScriptEngine engine, ScriptContext context,
                                         String scriptCode) throws Exception {
        Bindings engineScopeBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        Bindings globalScopeBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        context.setBindings(engineScopeBindings, ScriptContext.GLOBAL_SCOPE);
        // assign throw-away scope
        context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        try {
            return engine.eval(scriptCode, context);
        } finally {
            context.setBindings(engineScopeBindings, ScriptContext.ENGINE_SCOPE);
            context.setBindings(globalScopeBindings, ScriptContext.GLOBAL_SCOPE);
        }
    }


    public static void evalIntoEngineUTF8Resource(NashornScriptEngine scope, ScriptContext context, String name,
            Class<?> relativeTo) throws Exception {
        String content = Resources.toString(
                Resources.getResource(relativeTo, name), Charsets.UTF_8);
        evalIntoEngine(scope, context, name, content);
    }

    /**
     * Load lib into engine to be able to call it from other scripts or via
     * {@link #invokeFunction(NashornScriptEngine, ScriptContext, String, Object...)} or
     * {@link #invokeMethod(NashornScriptEngine, ScriptContext, Object, String, Object...)}.
     * You can provide a scope here, but mind that with nashorn code stays deeply associated with engine objects:
     * Warmup for example is currently required for each engine separately.
     *
     * @param engine MODIFIED, context is assigned to it if not set yet.
     * @param context gets the new code loaded into its global scope. Should have been created with
     * {@link #createContext(NashornScriptEngine)}.
     */
    public static void evalIntoEngine(NashornScriptEngine engine, ScriptContext context, String name,
                                      String content) throws Exception {

        Map<String, String> loadParam = new HashMap<>();
        loadParam.put("name", name);
        //loadParam.put("content", content);
        loadParam.put("script", content);

        //String tempVarName = "__NashornLoadingHelper";
        //context.getBindings(ScriptContext.ENGINE_SCOPE).put(tempVarName, loadParam);
        try {
            NashornHelper.invokeFunction(engine, context, "load", loadParam);
            //engine.eval(String.format("load({ name: %s.name, script: %s.content });", tempVarName, tempVarName),
            //        context);

        } finally {
            // discard temp var
            //context.getBindings(ScriptContext.ENGINE_SCOPE).remove(tempVarName);
        }
    }

    /**
     * A compatible require() implementation useful for loading common-js style modules. After this
     * the context 'knows' require.
     * @param engine MODIFIED, context is set to it.
     * @param context get require if not available yet. Must the require fake of NashornHelper - other requires don't
     *                work.
     * @param requireTargets read when required, module names/paths to CharSources that provide the source.
     *                       See {@link #addClasspathResources(LinkedHashMap, String, String...)}.
     */
    public static void loadWithRequire(NashornScriptEngine engine, ScriptContext context,
                                       LinkedHashMap<String, CharSource> requireTargets) throws Exception {
        loadWithRequire(engine, context, requireTargets, "de/deverado/framework/js/nashorn/require.js");
    }

    /**
     * A node-fake-compatible require() implementation useful for loading common-js style modules. After this
     * the context 'knows' require.
     * @param engine MODIFIED, context is set to it.
     * @param context get require if not available yet. Must the require fake of NashornHelper - other requires don't
     *                work.
     * @param requireTargets read when required, module names/paths to CharSources that provide the source.
     *                       See {@link #addClasspathResources(LinkedHashMap, String, String...)}.
     */
    public static void loadWithDebuggingFakeRequire(NashornScriptEngine engine, ScriptContext context,
                                                    LinkedHashMap<String, CharSource> requireTargets) throws Exception {
        loadWithRequire(engine, context, requireTargets, "de/deverado/framework/js/nashorn/requireFake.js");
    }

    static void loadWithRequire(NashornScriptEngine engine, ScriptContext context,
                                        LinkedHashMap<String, CharSource> requireTargets,
                                        String requireResourceName) throws Exception {

        engine.setContext(context);
        if (context.getAttribute("require") == null) {
            String requireScript;
            try {
                requireScript = Resources.asCharSource(Resources.getResource(requireResourceName),
                        Charsets.UTF_8).read();
            } catch (Exception e) {
                throw new RuntimeException("Missing a resource: " + e.getMessage(), e);
            }
            engine.eval(requireScript);
        }
        Object addTargetsFunc = NashornHelper.getScriptObject(engine, context, "require.__addTargets");
        Object requireObj = NashornHelper.getScriptObject(engine, context, "require");
        Preconditions.checkState(addTargetsFunc != null,
                "Wrong require loaded, expecting the fake require - otherwise you don't need this method.");

        NashornHelper.invokeMethod(engine, context, requireObj, "__addTargets", requireTargets);
    }

    public static void addClasspathResources(LinkedHashMap<String, CharSource> requireTargets, String classpathRoot,
                                             String... files) {
        for (String f : files) {
            URL resource = Resources.getResource(new File(classpathRoot, f).getPath());
            requireTargets.put(f, Resources.asCharSource(resource, Charsets.UTF_8));
        }
    }
}
