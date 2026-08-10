package io.github.sibmaks.jjtemplate.idea.lang;

import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateJsonSchemaIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void validatesMappedSchemaConstraints() {
        configureMappedSchema();

        var descriptions = getFixture().doHighlighting().stream()
                .map(info -> info.getDescription())
                .filter(description -> description != null)
                .toList();

        assertTrue(descriptions.stream().anyMatch(description -> description.contains("enabled")), descriptions::toString);
        assertTrue(descriptions.stream().anyMatch(description -> description.contains("allowed")), descriptions::toString);
        assertTrue(descriptions.stream().anyMatch(description -> description.contains("minimum")
                || description.contains("greater than or equal")), descriptions::toString);
        assertTrue(descriptions.stream().anyMatch(description -> description.contains("extra")), descriptions::toString);
    }

    @Test
    void doesNotValidateFileWhenSchemaIsDisabled() {
        configureMappedSchema();
        var target = getFixture().getFile().getVirtualFile();
        JsonSchemaMappingsProjectConfiguration.getInstance(getFixture().getProject()).markAsIgnored(target);
        JsonSchemaService.Impl.get(getFixture().getProject()).reset();

        var descriptions = getFixture().doHighlighting().stream()
                .map(info -> info.getDescription())
                .filter(description -> description != null)
                .toList();

        assertFalse(descriptions.stream().anyMatch(description -> description.contains("enabled")), descriptions::toString);
        assertFalse(descriptions.stream().anyMatch(description -> description.contains("allowed")), descriptions::toString);
        assertFalse(descriptions.stream().anyMatch(description -> description.contains("extra")), descriptions::toString);
    }

    private void configureMappedSchema() {
        var schema = getFixture().addFileToProject("schemas/template-schema.json", """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "required": ["template", "enabled"],
                  "properties": {
                    "template": {"type": "string", "enum": ["allowed"]},
                    "enabled": {"type": "boolean"},
                    "count": {"type": "integer", "minimum": 2}
                  },
                  "additionalProperties": false
                }
                """);
        getFixture().configureByText("schema-target.jjt", """
                {
                  "template": "wrong",
                  "count": 1,
                  "extra": true
                }
                """);
        var target = getFixture().getFile().getVirtualFile();
        var configuration = new UserDefinedJsonSchemaConfiguration(
                "JJTemplate test schema",
                JsonSchemaVersion.SCHEMA_7,
                schema.getVirtualFile().getUrl(),
                false,
                List.of(new UserDefinedJsonSchemaConfiguration.Item(
                        target.getUrl(),
                        JsonMappingKind.File
                ))
        );
        var mappings = JsonSchemaMappingsProjectConfiguration.getInstance(getFixture().getProject());
        mappings.setState(new LinkedHashMap<>());
        mappings.addConfiguration(configuration);
        assertNotNull(mappings.findMappingForFile(target), () -> "No mapping for " + target.getUrl()
                + " in " + mappings.getStateMap());
        var service = JsonSchemaService.Impl.get(getFixture().getProject());
        service.reset();
        assertTrue(service.isApplicableToFile(target));
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
