package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateAnnotatorIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void marksEveryGlobalVariableUsageOnErrorStripe() {
        var source = """
                {
                  "definitions": [
                    {"local": 1},
                    {"localUsage": "{{ .local }}"},
                    {"futureUsage": "{{ .future }}"},
                    {"future": 2},
                    {"{{ parts range item,index of .items }}": ["{{ .item }}"]}
                  ],
                  "template": "{{ .external }} {{ .external }} {{ .localUsage }}"
                }
                """;
        getFixture().configureByText("globals.jjt", source);

        var globalHighlights = getFixture().doHighlighting().stream()
                .filter(info -> info.getDescription() != null)
                .filter(info -> info.getDescription().startsWith("Global variable '"))
                .toList();

        assertEquals(
                List.of("future", "items", "external", "external"),
                globalHighlights.stream()
                        .map(info -> source.substring(info.startOffset, info.endOffset))
                        .toList()
        );
        globalHighlights.forEach(JJTemplateAnnotatorIntegrationTest::assertErrorStripeMarker);
    }

    @Test
    void doesNotMarkPropertyAfterMethodCallAsGlobalVariable() {
        var source = """
                {
                  "template": "{{ .attachements.get(0).sub }}"
                }
                """;
        getFixture().configureByText("method-property.jjt", source);

        var globalNames = getFixture().doHighlighting().stream()
                .filter(info -> info.getDescription() != null)
                .filter(info -> info.getDescription().startsWith("Global variable '"))
                .map(info -> source.substring(info.startOffset, info.endOffset))
                .toList();

        assertEquals(List.of("attachements"), globalNames);
    }

    @Test
    void reportsDuplicateKeysInSameObject() {
        var source = """
                {
                  "template": "first",
                  "template": "second",
                  "nested": {
                    "template": "nested"
                  }
                }
                """;
        getFixture().configureByText("duplicate-key.jjt", source);

        var duplicateHighlights = getFixture().doHighlighting().stream()
                .filter(info -> "Object contains duplicate keys 'template'".equals(info.getDescription()))
                .toList();

        assertEquals(2, duplicateHighlights.size());
        assertEquals(
                List.of("\"template\"", "\"template\""),
                duplicateHighlights.stream()
                        .map(info -> source.substring(info.startOffset, info.endOffset))
                        .toList()
        );
        duplicateHighlights.forEach(info -> assertEquals(HighlightSeverity.WARNING, info.getSeverity()));
    }

    @Test
    void reportsDuplicateKeysInNestedObject() {
        var source = """
                {
                  "template": "root",
                  "nested": {
                    "value": 1,
                    "value": 2
                  }
                }
                """;
        getFixture().configureByText("nested-duplicate-key.jjt", source);

        var duplicateHighlights = getFixture().doHighlighting().stream()
                .filter(info -> "Object contains duplicate keys 'value'".equals(info.getDescription()))
                .toList();

        assertEquals(2, duplicateHighlights.size());
        assertEquals(
                List.of("\"value\"", "\"value\""),
                duplicateHighlights.stream()
                        .map(info -> source.substring(info.startOffset, info.endOffset))
                        .toList()
        );
        duplicateHighlights.forEach(info -> assertEquals(HighlightSeverity.WARNING, info.getSeverity()));
    }

    @Test
    void reportsDuplicateDeclaredVariablesInTemplatedObjectKeys() {
        var source = """
                {
                  "template": {
                    "{{ key1 range key,value of .object }}": {},
                    "{{ key1 switch .some }}": {}
                  }
                }
                """;
        getFixture().configureByText("duplicate-template-variable.jjt", source);

        var duplicateHighlights = getFixture().doHighlighting().stream()
                .filter(info -> "Object contains duplicate keys 'key1'".equals(info.getDescription()))
                .toList();

        assertEquals(2, duplicateHighlights.size());
        assertEquals(
                List.of("key1", "key1"),
                duplicateHighlights.stream()
                        .map(info -> source.substring(info.startOffset, info.endOffset))
                        .toList()
        );
        duplicateHighlights.forEach(info -> assertEquals(HighlightSeverity.WARNING, info.getSeverity()));
    }

    @Test
    void allowsSameKeyInDifferentObjectScopes() {
        getFixture().configureByText("scoped-keys.jjt", """
                {
                  "template": "root",
                  "first": {
                    "value": 1
                  },
                  "second": {
                    "value": 2
                  }
                }
                """);

        var hasDuplicateHighlight = getFixture().doHighlighting().stream()
                .map(HighlightInfo::getDescription)
                .filter(description -> description != null)
                .anyMatch(description -> description.startsWith("Object contains duplicate keys"));

        assertFalse(hasDuplicateHighlight);
    }

    private static void assertErrorStripeMarker(HighlightInfo info) {
        assertNotNull(info.forcedTextAttributes);
        assertNotNull(info.forcedTextAttributes.getErrorStripeColor());
    }

    @Override
    protected @NotNull String getRelativePath() {
        return "";
    }

    @Override
    protected String getTestDataPath() {
        return "";
    }
}
