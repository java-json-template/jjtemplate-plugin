package io.github.sibmaks.jjtemplate.idea.completion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuiltInFunctionIndexTest {

    @Test
    void exposesEveryCompilerBuiltInWithoutClasspathScanning() {
        var functions = BuiltInFunctionIndex.list();

        assertEquals(57, functions.size());
        assertNotNull(BuiltInFunctionIndex.find("default"));
        assertNotNull(BuiltInFunctionIndex.find("cast:str"));
        assertNotNull(BuiltInFunctionIndex.find("map:new"));
    }

    @Test
    void exposesLazyArgumentEvaluationForShortCircuitFunctions() {
        assertLazy("default");
        assertLazy("and");
        assertLazy("or");
    }

    @Test
    void exposesEagerArgumentEvaluationForRegularFunctions() {
        var function = BuiltInFunctionIndex.find("eq");

        assertNotNull(function);
        assertFalse(function.lazy());
    }

    @Test
    void exposesSignatureAndDescriptionForEveryBuiltInFunction() {
        for (var function : BuiltInFunctionIndex.list()) {
            assertFalse(function.signature().endsWith("(...)"), function.lookupKey());
            assertNotEquals("Built-in JJTemplate function.", function.description(), function.lookupKey());
        }
    }

    @Test
    void exposesCastStringDocumentation() {
        var function = BuiltInFunctionIndex.find("cast:str");

        assertNotNull(function);
        assertEquals("cast:str(value)", function.signature());
        assertEquals("Converts a value to a string.", function.description());
    }

    private static void assertLazy(String name) {
        var function = BuiltInFunctionIndex.find(name);

        assertNotNull(function);
        assertTrue(function.lazy());
    }
}
