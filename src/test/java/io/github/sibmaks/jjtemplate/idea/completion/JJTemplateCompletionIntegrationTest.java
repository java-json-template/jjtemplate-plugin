package io.github.sibmaks.jjtemplate.idea.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import io.github.sibmaks.jjtemplate.idea.lang.JJTemplateTypedHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateCompletionIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    private static Set<String> lookupStrings(LookupElement[] lookups) {
        assertTrue(lookups != null && lookups.length > 0);
        return Arrays.stream(lookups)
                .map(LookupElement::getLookupString)
                .collect(Collectors.toSet());
    }

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
        assertNotNull(presentation.getItemText());
        assertTrue(presentation.getItemText().startsWith("map:new("));
        assertNotNull(presentation.getTypeText());
        assertTrue(presentation.getTypeText().startsWith("Creates a map"));
    }

    @Test
    void initiallySuggestsNamespacesAndOnlyFunctionsWithoutNamespace() {
        getFixture().configureByText("top-level.jjt", """
                {
                  "template": "{{ <caret> }}"
                }
                """);

        var lookups = lookupStrings(getFixture().completeBasic());

        assertTrue(lookups.contains("cast"), lookups::toString);
        assertTrue(lookups.contains("map"), lookups::toString);
        assertTrue(lookups.contains("default"), lookups::toString);
        assertFalse(lookups.contains("cast:str"), lookups::toString);
        assertFalse(lookups.contains("map:new"), lookups::toString);
    }

    @Test
    void suggestsNamespaceForTypedPrefix() {
        getFixture().configureByText("namespace-prefix.jjt", """
                {
                  "template": "{{ c<caret> }}"
                }
                """);

        var lookups = lookupStrings(getFixture().complete(CompletionType.BASIC, 0));

        assertTrue(lookups.contains("cast"), lookups::toString);
    }

    @Test
    void acceptingNamespacePlacesCaretAfterColon() {
        getFixture().configureByText("namespace-insertion.jjt", """
                {
                  "template": "{{ cas<caret> }}"
                }
                """);

        getFixture().completeBasic();

        getFixture().checkResult("""
                {
                  "template": "{{ cast:<caret> }}"
                }
                """);
    }

    @Test
    void suggestsFunctionsImmediatelyAfterNamespaceColon() {
        getFixture().configureByText("namespace-colon.jjt", """
                {
                  "template": "{{ cast:<caret> }}"
                }
                """);

        var lookups = lookupStrings(getFixture().completeBasic());

        assertTrue(lookups.contains("str"), lookups::toString);
        assertTrue(lookups.contains("int"), lookups::toString);
        assertFalse(lookups.contains("new"), lookups::toString);
    }

    @Test
    void namespaceColonTriggersAutomaticCompletion() {
        getFixture().configureByText("namespace-auto-popup.jjt", """
                {
                  "template": "{{ cast:<caret> }}"
                }
                """);

        var result = ReadAction.compute(() -> new JJTemplateTypedHandler().checkAutoPopup(
                ':',
                getFixture().getProject(),
                getFixture().getEditor(),
                getFixture().getFile()
        ));

        assertSame(result, TypedHandlerDelegate.Result.STOP);
    }

    @Test
    void suggestsBuiltInFunctionsOnlyInsideTemplateExpressions() {
        getFixture().configureByText("outside-expression.jjt", """
                {
                  <caret>"template": "constant"
                }
                """);

        var lookups = getFixture().completeBasic();

        assertTrue(lookups == null || Arrays.stream(lookups)
                .map(LookupElement::getLookupString)
                .noneMatch(lookup -> lookup.equals("cast") || lookup.equals("default")));
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

    @Test
    void suggestsVariablesForPrefixBeforeExistingDot() {
        getFixture().configureByText("variable-before-dot.jjt", """
                {
                  "definitions": [
                    {
                      "{{ attachements range key,value of .object }}": "{{ .key }} = {{ .value }}"
                    }
                  ],
                  "template": "{{ at<caret>. }}"
                }
                """);

        var lookups = lookupStrings(getFixture().completeBasic());

        assertTrue(lookups.contains("attachements"), lookups::toString);
    }

    @Test
    void doesNotSuggestCompletionForNumericPrefix() {
        getFixture().configureByText("number-before-dot.jjt", """
                {
                  "definitions": [
                    {
                      "{{ attachements range key,value of .object }}": "{{ .key }} = {{ .value }}"
                    }
                  ],
                  "template": "{{ 1<caret>. }}"
                }
                """);

        var lookups = getFixture().completeBasic();

        assertTrue(lookups == null || lookups.length == 0, () -> Arrays.toString(lookups));

        getFixture().configureByText("number.jjt", """
                {
                  "template": "{{ 1<caret> }}"
                }
                """);

        var plainNumberLookups = getFixture().completeBasic();

        assertTrue(
                plainNumberLookups == null || plainNumberLookups.length == 0,
                () -> Arrays.toString(plainNumberLookups)
        );
    }

    @Test
    void doesNotSuggestVariablesInPropertyChains() {
        assertNoCompletions("property-chain-dot.jjt", "{{ .attachements.<caret> }}");
        assertNoCompletions("property-chain-prefix.jjt", "{{ .attachements.na<caret> }}");
        assertNoCompletions("safe-property-chain-dot.jjt", "{{ .attachements?.<caret> }}");
        assertNoCompletions("safe-property-chain-prefix.jjt", "{{ .attachements?.na<caret> }}");
    }

    private void assertNoCompletions(String fileName, String expression) {
        getFixture().configureByText(fileName, """
                {
                  "definitions": [
                    {
                      "{{ attachements range key,value of .object }}": "{{ .key }} = {{ .value }}"
                    }
                  ],
                  "template": "%s"
                }
                """.formatted(expression));

        var lookups = getFixture().completeBasic();

        assertTrue(lookups == null || lookups.length == 0, () -> Arrays.toString(lookups));
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
