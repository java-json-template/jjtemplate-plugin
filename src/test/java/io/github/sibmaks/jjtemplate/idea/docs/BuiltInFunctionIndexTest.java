package io.github.sibmaks.jjtemplate.idea.docs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInFunctionIndexTest {

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

    private static void assertLazy(String name) {
        var function = BuiltInFunctionIndex.find(name);

        assertNotNull(function);
        assertTrue(function.lazy());
    }
}
