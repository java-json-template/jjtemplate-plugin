package io.github.sibmaks.jjtemplate.idea.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JjtemplateCompletionContributorTest {

    @Test
    void builtInFunctionSuggestionShowsSignatureAndDescription() {
        var function = BuiltInFunctionIndex.find("cast:str");
        assertNotNull(function);
        var lookup = JjtemplateCompletionContributor.createFunctionLookup(function, function.presentableName());
        var presentation = new LookupElementPresentation();

        lookup.renderElement(presentation);

        assertEquals("cast:str", lookup.getLookupString());
        assertEquals("cast:str(value)", presentation.getItemText());
        assertEquals("Converts a value to a string.", presentation.getTypeText());
    }

    @Test
    void namespaceSuggestionInsertsOnlyFunctionName() {
        var function = BuiltInFunctionIndex.find("cast:str");
        assertNotNull(function);

        var lookup = JjtemplateCompletionContributor.createFunctionLookup(function, function.name())
                .withLookupString(function.presentableName());

        assertEquals("str", lookup.getLookupString());
        assertTrue(lookup.getAllLookupStrings().contains("cast:str"));
    }

    @Test
    void resolvesSingleColonNamespaceCompletion() {
        var context = JjtemplateCompletionContributor.resolveNamespaceToken("cast:st");

        assertNotNull(context);
        assertEquals("cast", context.namespace());
        assertEquals("st", context.functionPrefix());
    }

    @Test
    void namespaceSuggestionInsertsColon() {
        var lookup = JjtemplateCompletionContributor.createNamespaceLookup("cast");
        var presentation = new LookupElementPresentation();

        lookup.renderElement(presentation);

        assertEquals("cast", lookup.getLookupString());
        assertEquals("cast", presentation.getItemText());
        assertEquals("function namespace", presentation.getTypeText());
    }
}
