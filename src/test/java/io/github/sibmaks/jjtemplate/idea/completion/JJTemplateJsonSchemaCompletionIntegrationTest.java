package io.github.sibmaks.jjtemplate.idea.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateJsonSchemaCompletionIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void suggestsPropertyNamesFromMappedSchema() {
        configureMappedSchema("""
                {
                  "<caret>": "constant"
                }
                """);

        var lookups = lookupStrings();
        assertTrue(lookups.contains("template"), lookups::toString);
    }

    @Test
    void suggestsAllowedValuesFromMappedSchema() {
        configureMappedSchema("""
                {
                  "template": "constant",
                  "mode": "<caret>"
                }
                """);

        var lookups = lookupStrings();
        assertTrue(lookups.contains("development"), lookups::toString);
        assertTrue(lookups.contains("production"), lookups::toString);
    }

    @Test
    void suggestsPropertyNamesBeforeQuotesAreEntered() {
        configureMappedSchema("""
                {
                  <caret>
                }
                """);

        var lookups = lookupStrings();
        assertTrue(lookups.contains("template"), lookups::toString);
        assertTrue(lookups.contains("mode"), lookups::toString);
    }

    @Test
    void suggestsBooleanValuesForIncompleteJsonValue() {
        configureMappedSchema("""
                {
                  "enabled": <caret>
                }
                """);

        var lookups = lookupStrings();
        assertTrue(lookups.contains("true"), lookups::toString);
        assertTrue(lookups.contains("false"), lookups::toString);
    }

    @Test
    void insertsUnquotedPropertyWithJsonSyntax() {
        configureMappedSchema("""
                {
                  tem<caret>
                }
                """);

        chooseCompletion("template");

        getFixture().checkResult("""
                {
                  "template": <caret>
                }
                """);
    }

    @Test
    void insertsStringEnumInsideExistingQuotes() {
        configureMappedSchema("""
                {
                  "mode": "dev<caret>"
                }
                """);

        chooseCompletion("development");

        getFixture().checkResult("""
                {
                  "mode": "development<caret>"
                }
                """);
    }

    @Test
    void resolvesNestedPropertyAndValueSchemas() {
        configureMappedSchema("""
                {
                  "settings": {
                    "<caret>": true
                  }
                }
                """);

        var propertyLookups = lookupStrings();
        assertTrue(propertyLookups.contains("strategy"), propertyLookups::toString);
        assertTrue(propertyLookups.contains("retry"), propertyLookups::toString);

        getFixture().configureByText("schema-completion.jjt", """
                {
                  "settings": {
                    "strategy": "<caret>"
                  }
                }
                """);

        var valueLookups = lookupStrings();
        assertTrue(valueLookups.contains("fast"), valueLookups::toString);
        assertTrue(valueLookups.contains("safe"), valueLookups::toString);
    }

    @Test
    void suggestsAllowedValuesForArrayItems() {
        configureMappedSchema("""
                {
                  "items": ["<caret>"]
                }
                """);

        var lookups = lookupStrings();
        assertTrue(lookups.contains("red"), lookups::toString);
        assertTrue(lookups.contains("blue"), lookups::toString);
    }

    @Test
    void doesNotSuggestSchemaVariantsWhenSchemaIsDisabled() {
        configureMappedSchema("""
                {
                  "<caret>": "constant"
                }
                """);
        var target = getFixture().getFile().getVirtualFile();
        JsonSchemaMappingsProjectConfiguration.getInstance(getFixture().getProject()).markAsIgnored(target);
        JsonSchemaService.Impl.get(getFixture().getProject()).reset();

        var lookups = getFixture().complete(CompletionType.BASIC, 0);

        assertFalse(lookupStrings(lookups).contains("template"));
    }

    private Set<String> lookupStrings() {
        var lookups = getFixture().complete(CompletionType.BASIC, 0);
        assertNotNull(lookups);
        return lookupStrings(lookups);
    }

    private void chooseCompletion(String lookupString) {
        var lookups = getFixture().completeBasic();
        if (lookups == null) {
            return;
        }
        var lookup = Arrays.stream(lookups)
                .filter(item -> lookupString.equals(item.getLookupString()))
                .findFirst()
                .orElseThrow();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            getFixture().getLookup().setCurrentItem(lookup);
            getFixture().finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });
    }

    private static Set<String> lookupStrings(LookupElement[] lookups) {
        if (lookups == null) {
            return Set.of();
        }
        return Arrays.stream(lookups)
                .map(LookupElement::getLookupString)
                .collect(Collectors.toSet());
    }

    private void configureMappedSchema(String targetText) {
        var schema = getFixture().addFileToProject("schemas/completion-schema.json", """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "template": {"type": "string"},
                    "mode": {
                      "type": "string",
                      "enum": ["development", "production"]
                    },
                    "enabled": {"type": "boolean"},
                    "settings": {
                      "type": "object",
                      "properties": {
                        "strategy": {"type": "string", "enum": ["fast", "safe"]},
                        "retry": {"type": "integer"}
                      }
                    },
                    "items": {
                      "type": "array",
                      "items": {"type": "string", "enum": ["red", "blue"]}
                    }
                  },
                  "additionalProperties": false
                }
                """);
        getFixture().configureByText("schema-completion.jjt", targetText);
        var target = getFixture().getFile().getVirtualFile();
        var configuration = new UserDefinedJsonSchemaConfiguration(
                "JJTemplate completion schema",
                JsonSchemaVersion.SCHEMA_7,
                schema.getVirtualFile().getUrl(),
                false,
                List.of(new UserDefinedJsonSchemaConfiguration.Item(target.getUrl(), JsonMappingKind.File))
        );
        var mappings = JsonSchemaMappingsProjectConfiguration.getInstance(getFixture().getProject());
        mappings.setState(new LinkedHashMap<>());
        mappings.addConfiguration(configuration);
        var service = JsonSchemaService.Impl.get(getFixture().getProject());
        service.reset();
        assertNotNull(ReadAction.compute(() -> service.getSchemaObject(getFixture().getFile())));
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
