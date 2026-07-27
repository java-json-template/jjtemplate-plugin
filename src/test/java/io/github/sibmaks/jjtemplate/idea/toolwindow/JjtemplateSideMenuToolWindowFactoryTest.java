package io.github.sibmaks.jjtemplate.idea.toolwindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sibmaks.jjtemplate.compiler.api.TemplateCompiler;
import io.github.sibmaks.jjtemplate.compiler.api.TemplateScript;
import io.github.sibmaks.jjtemplate.compiler.optimizer.CompiledTemplateFolder;
import io.github.sibmaks.jjtemplate.compiler.optimizer.UnusedVariableNodeEliminator;
import io.github.sibmaks.jjtemplate.compiler.optimizer.VariableNodeInliner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JjtemplateSideMenuToolWindowFactoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void optimizeSourceAppliesInliningAndUnusedDefinitionElimination() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "used": "optimized",
                      "unused": "removed"
                    }
                  ],
                  "template": "{{ .used }}"
                }
                """;

        var optimized = JjtemplateSideMenuToolWindowFactory.optimizeSource(
                source,
                List.of(
                        new CompiledTemplateFolder(),
                        new VariableNodeInliner(),
                        new UnusedVariableNodeEliminator()
                ),
                2
        );
        var optimizedJson = MAPPER.readTree(optimized);

        assertEquals("optimized", optimizedJson.path("template").asText());
        assertTrue(optimizedJson.path("definitions").isEmpty());
        assertFalse(optimized.contains("unused"));
    }

    @Test
    void optimizeSourceLeavesPassesDisabled() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "used": "optimized",
                      "unused": "retained"
                    }
                  ],
                  "template": "{{ .used }}"
                }
                """;

        var optimized = JjtemplateSideMenuToolWindowFactory.optimizeSource(source, List.of(), 4);
        var optimizedJson = MAPPER.readTree(optimized);

        assertEquals(2, optimizedJson.path("definitions").path(0).size());
        assertTrue(optimized.contains("retained"));
        assertTrue(optimized.contains("\n    \""));
    }

    @Test
    void optimizeSourceRendersConcatenationsWhenConstantFoldingIsDisabled() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "answer": 42
                    }
                  ],
                  "template": {
                    "message": "Answer: {{ .answer }}"
                  }
                }
                """;

        var optimized = JjtemplateSideMenuToolWindowFactory.optimizeSource(
                source,
                List.of(
                        new VariableNodeInliner(),
                        new UnusedVariableNodeEliminator()
                ),
                2
        );
        var optimizedScript = MAPPER.readValue(optimized, TemplateScript.class);
        var rendered = TemplateCompiler.getInstance().compile(optimizedScript).render();

        assertEquals(Map.of("message", "Answer: 42"), rendered);
    }

    @Test
    void optimizeSourceRendersValidJjtemplateWithSubstitutions() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "answer": 42,
                      "unused": "removed"
                    }
                  ],
                  "template": {
                    "folded": "Answer: {{ .answer }}",
                    "substitution": "{{ .name }}"
                  }
                }
                """;

        var optimized = JjtemplateSideMenuToolWindowFactory.optimizeSource(
                source,
                List.of(
                        new CompiledTemplateFolder(),
                        new VariableNodeInliner(),
                        new UnusedVariableNodeEliminator()
                ),
                2
        );
        var optimizedScript = MAPPER.readValue(optimized, TemplateScript.class);
        var rendered = TemplateCompiler.getInstance().compile(optimizedScript).render(Map.of("name", "JJTemplate"));

        assertEquals(
                Map.of(
                        "folded", "Answer: 42",
                        "substitution", "JJTemplate"
                ),
                rendered
        );
        assertTrue(optimized.contains("\"substitution\" : \"{{ .name }}\""));
        assertFalse(optimized.contains("unused"));
    }

    @Test
    void optimizeSourcePreservesRangeSwitchAndCollectionSubstitutions() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "{{ label switch .enabled }}": {
                        "{{ true }}": "enabled",
                        "{{ else }}": "disabled"
                      }
                    }
                  ],
                  "template": {
                    "label": "{{ .label }}",
                    "mapped": {
                      "{{ values range item,index of .values }}": "{{ .index }}:{{ .item }}"
                    },
                    "spread": ["first", "{{. .values }}"],
                    "conditional": ["{{? .optional }}"]
                  }
                }
                """;
        var context = Map.<String, Object>of(
                "enabled", true,
                "values", List.of("a", "b")
        );
        var compiler = TemplateCompiler.getInstance();
        var expected = compiler.compile(MAPPER.readValue(source, TemplateScript.class)).render(context);

        var optimized = JjtemplateSideMenuToolWindowFactory.optimizeSource(
                source,
                List.of(
                        new CompiledTemplateFolder(),
                        new VariableNodeInliner(),
                        new UnusedVariableNodeEliminator()
                ),
                2
        );
        var optimizedScript = MAPPER.readValue(optimized, TemplateScript.class);
        var actual = compiler.compile(optimizedScript).render(context);

        assertEquals(expected, actual);
    }
}
