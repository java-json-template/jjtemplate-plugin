package io.github.sibmaks.jjtemplate.idea.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.CloseResource")
class JjtemplateCompletionIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void suggestsNamespacedFunctionWithDescription() {
        getFixture().configureByText("namespace.jjt", """
                {
                  "template": "{{ map:n<caret> }}"
                }
                """);

        var lookups = getFixture().completeBasic();
        var lookupStrings = lookupStrings(lookups);

        assertTrue(lookupStrings.contains("new"), lookupStrings::toString);
        var newFunction = Arrays.stream(lookups)
                .filter(lookup -> "new".equals(lookup.getLookupString()))
                .findFirst()
                .orElseThrow();
        var presentation = new LookupElementPresentation();
        newFunction.renderElement(presentation);
        assertTrue(presentation.getItemText().startsWith("map:new("));
        assertTrue(presentation.getTypeText().startsWith("Creates a map"));
    }

    @Test
    void suggestsRangeBindingsOnlyInsideRangeValue() {
        getFixture().configureByText("inside-range.jjt", """
                {
                  "definitions": [
                    {
                      "{{ parts range item,index of .items }}": [
                        "{{ .<caret> }}"
                      ]
                    }
                  ],
                  "template": "constant"
                }
                """);

        var inside = lookupStrings(getFixture().completeBasic());

        assertTrue(inside.contains("item"), inside::toString);
        assertTrue(inside.contains("index"), inside::toString);

        getFixture().configureByText("outside-range.jjt", """
                {
                  "definitions": [
                    {
                      "{{ parts range item,index of .items }}": [
                        "{{ .item }}"
                      ]
                    }
                  ],
                  "template": "{{ .<caret> }}"
                }
                """);

        var outside = lookupStrings(getFixture().completeBasic());

        assertFalse(outside.contains("item"), outside::toString);
        assertFalse(outside.contains("index"), outside::toString);
    }

    private static Set<String> lookupStrings(LookupElement[] lookups) {
        assertTrue(lookups != null && lookups.length > 0);
        return Arrays.stream(lookups)
                .map(LookupElement::getLookupString)
                .collect(Collectors.toSet());
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
