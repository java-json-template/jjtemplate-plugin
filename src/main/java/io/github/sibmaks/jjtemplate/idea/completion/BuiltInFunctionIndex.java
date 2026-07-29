package io.github.sibmaks.jjtemplate.idea.completion;

import io.github.sibmaks.jjtemplate.compiler.runtime.fun.TemplateFunction;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuiltInFunctionIndex {
    private static final String BASE_PACKAGE = "io.github.sibmaks.jjtemplate.compiler.runtime.fun";
    private static final Map<String, FunctionDocumentation> DOCUMENTATION = Map.ofEntries(
            doc("cast:str", "cast:str(value)", "Converts a value to a string."),
            doc("cast:int", "cast:int(value)", "Converts a value to an integer."),
            doc("cast:float", "cast:float(value)", "Converts a value to a decimal number."),
            doc("cast:boolean", "cast:boolean(value)", "Converts a value to a boolean."),
            doc("string:concat", "string:concat(base, ...values)", "Concatenates strings."),
            doc("string:join", "string:join(glue, ...values)", "Joins strings with a separator."),
            doc("string:joinNotEmpty", "string:joinNotEmpty(glue, ...values)",
                    "Joins non-null, non-empty strings with a separator."),
            doc("string:len", "string:len(string)", "Returns the string length."),
            doc("string:empty", "string:empty(string)", "Checks whether a string is null or empty."),
            doc("string:contains", "string:contains(string, ...substrings)",
                    "Checks whether the string contains every substring."),
            doc("string:format", "string:format([locale], pattern, ...args)", "Formats a string."),
            doc("string:lower", "string:lower([locale], value)", "Converts a string to lowercase."),
            doc("string:upper", "string:upper([locale], value)", "Converts a string to uppercase."),
            doc("string:trim", "string:trim(value)", "Removes leading and trailing whitespace."),
            doc("string:split", "string:split(value, regex, [limit])",
                    "Splits a string around regular-expression matches."),
            doc("string:indexOf", "string:indexOf(value, string)",
                    "Returns the index of the first occurrence."),
            doc("string:lastIndexOf", "string:lastIndexOf(value, string)",
                    "Returns the index of the last occurrence."),
            doc("string:substr", "string:substr(value, beginIndex, [endIndex])",
                    "Returns a substring and supports negative indexes."),
            doc("string:replace", "string:replace(value, target, replacement)",
                    "Replaces literal target sequences."),
            doc("string:replaceAll", "string:replaceAll(value, regex, replacement)",
                    "Replaces all regular-expression matches."),
            doc("list:new", "list:new(...items)", "Creates a list."),
            doc("list:concat", "list:concat(...lists)", "Concatenates lists or arrays."),
            doc("list:len", "list:len(list)", "Returns the list size."),
            doc("list:empty", "list:empty(list)", "Checks whether a list is empty."),
            doc("list:contains", "list:contains(list, ...values)",
                    "Checks whether a list contains every value."),
            doc("list:head", "list:head(list)", "Returns the first item, or null."),
            doc("list:tail", "list:tail(list)", "Returns all items except the first."),
            doc("list:join", "list:join(glue, ...lists)", "Joins list items into a string."),
            doc("map:new", "map:new(key, value, ...)", "Creates a map from key-value pairs."),
            doc("map:len", "map:len(map)", "Returns the number of map entries."),
            doc("map:empty", "map:empty(map)", "Checks whether a map is empty."),
            doc("map:contains", "map:contains(map, ...keys)", "Checks whether a map contains every key."),
            doc("map:collapse", "map:collapse(object|array|collection)",
                    "Merges object properties into one map."),
            doc("date:format", "date:format([locale], pattern, date)", "Formats a date."),
            doc("date:parse", "date:parse(pattern, string)", "Parses a string into a LocalDate."),
            doc("date:now", "date:now()", "Returns the current LocalDate."),
            doc("datetime:parse", "datetime:parse(pattern, string)",
                    "Parses a string into a LocalDateTime."),
            doc("datetime:now", "datetime:now()", "Returns the current LocalDateTime."),
            doc("locale:new", "locale:new(language[, country[, variant]])", "Creates a Locale."),
            doc("numberFormat:new", "numberFormat:new(locale[, settings])", "Creates a NumberFormat."),
            doc("math:neg", "math:neg(value)", "Negates a numeric value."),
            doc("math:sum", "math:sum(left, right)", "Adds two numeric values."),
            doc("math:sub", "math:sub(left, right)", "Subtracts two numeric values."),
            doc("math:mul", "math:mul(left, right)", "Multiplies two numeric values."),
            doc("math:div", "math:div(left, right, [mode])", "Divides two numeric values."),
            doc("math:scale", "math:scale(value, amount, mode)", "Changes a decimal value's scale."),
            doc("default", "default(value, fallback)", "Returns the fallback when value is null."),
            doc("not", "not(value)", "Inverts a boolean value."),
            doc("eq", "eq(a, b)", "Checks whether two values are equal."),
            doc("neq", "neq(a, b)", "Checks whether two values are different."),
            doc("lt", "lt(a, b)", "Checks whether a is less than b."),
            doc("le", "le(a, b)", "Checks whether a is less than or equal to b."),
            doc("gt", "gt(a, b)", "Checks whether a is greater than b."),
            doc("ge", "ge(a, b)", "Checks whether a is greater than or equal to b."),
            doc("and", "and(a, b)", "Returns the logical conjunction."),
            doc("or", "or(a, b)", "Returns the logical disjunction."),
            doc("xor", "xor(a, b)", "Returns the exclusive logical disjunction.")
    );
    private static final List<BuiltInFunctionInfo> FUNCTIONS = loadFunctions();
    private static final Map<String, BuiltInFunctionInfo> BY_LOOKUP = buildLookup(FUNCTIONS);

    private BuiltInFunctionIndex() {
    }

    public static List<BuiltInFunctionInfo> list() {
        return FUNCTIONS;
    }

    public static BuiltInFunctionInfo find(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        var direct = BY_LOOKUP.get(key);
        if (direct != null) {
            return direct;
        }
        return BY_LOOKUP.get(key.replace("::", ":"));
    }

    private static Map<String, BuiltInFunctionInfo> buildLookup(List<BuiltInFunctionInfo> functions) {
        var result = new HashMap<String, BuiltInFunctionInfo>();
        for (var function : functions) {
            result.putIfAbsent(function.lookupKey(), function);
            result.putIfAbsent(function.lookupKey().replace("::", ":"), function);
            result.putIfAbsent(function.name(), function);
        }
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static List<BuiltInFunctionInfo> loadFunctions() {
        var result = new ArrayList<BuiltInFunctionInfo>();
        for (var type : findClasses(BASE_PACKAGE)) {
            if (!TemplateFunction.class.isAssignableFrom(type) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            try {
                var functionType = (Class<TemplateFunction<?>>) type;
                var constructor = functionType.getDeclaredConstructor();
                constructor.setAccessible(true);
                var function = constructor.newInstance();
                var namespace = Optional.ofNullable(function.getNamespace()).orElse("");
                var name = function.getName();
                var lookupKey = namespace.isEmpty() ? name : namespace + "::" + name;
                var documentationKey = namespace.isEmpty() ? name : namespace + ":" + name;
                var documentation = DOCUMENTATION.get(documentationKey);
                var presentableName = namespace.isEmpty() ? name : namespace + ":" + name;
                result.add(new BuiltInFunctionInfo(
                        lookupKey,
                        namespace,
                        name,
                        documentation == null ? presentableName + "(...)" : documentation.signature(),
                        documentation == null ? "Built-in JJTemplate function." : documentation.description(),
                        function.isLazy()
                ));
            } catch (Exception ignored) {
                // ignore non-instantiable classes
            }
        }
        result.sort(Comparator.comparing(BuiltInFunctionInfo::lookupKey));
        return Collections.unmodifiableList(result);
    }

    private static Map.Entry<String, FunctionDocumentation> doc(
            String key,
            String signature,
            String description
    ) {
        return Map.entry(key, new FunctionDocumentation(signature, description));
    }

    private static List<Class<?>> findClasses(String basePackage) {
        var classes = new ArrayList<Class<?>>();
        var path = basePackage.replace('.', '/');
        try {
            var resources = Thread.currentThread().getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                var resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    var directory = new java.io.File(resource.toURI());
                    collectFromDirectory(directory, basePackage, classes);
                    continue;
                }
                if ("jar".equals(resource.getProtocol())) {
                    collectFromJar(resource.getPath(), path, classes);
                }
            }
        } catch (Exception ignored) {
            return classes;
        }
        return classes;
    }

    private static void collectFromJar(String resourcePath, String packagePath, List<Class<?>> classes) {
        var separator = resourcePath.indexOf("!");
        if (separator < 0) {
            return;
        }
        var jarPath = resourcePath.substring(5, separator);
        try (var jar = new java.util.jar.JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var entryName = entry.getName();
                if (!entryName.startsWith(packagePath) || !entryName.endsWith(".class") || entryName.contains("$")) {
                    continue;
                }
                var className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                try {
                    classes.add(Class.forName(className));
                } catch (Exception ignored) {
                    // ignore loading failures
                }
            }
        } catch (Exception ignored) {
            // ignore loading failures
        }
    }

    private static void collectFromDirectory(java.io.File directory, String basePackage, List<Class<?>> classes) {
        var files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (var file : files) {
            if (file.isDirectory()) {
                collectFromDirectory(file, basePackage + "." + file.getName(), classes);
                continue;
            }
            var fileName = file.getName();
            if (!fileName.endsWith(".class") || fileName.contains("$")) {
                continue;
            }
            var className = basePackage + '.' + fileName.substring(0, fileName.length() - 6);
            try {
                classes.add(Class.forName(className));
            } catch (Exception ignored) {
                // ignore loading failures
            }
        }
    }

    private record FunctionDocumentation(String signature, String description) {
    }
}
