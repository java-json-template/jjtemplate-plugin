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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JjtemplateSideMenuToolWindowFactoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void compileSourceSupportsWrappedDefinitionExpressions() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "value": "text"
                    },
                    {
                      "{{ result switch .value }}": {
                        "{{ 'text' }}": "matched",
                        "{{ else }}": "fallback"
                      }
                    }
                  ],
                  "template": "{{ .result }}"
                }
                """;

        var rendered = JjtemplateSideMenuToolWindowFactory.compileSource(source, "{}");

        assertEquals("matched", rendered);
    }

    @Test
    void compileSourceUsesModernCompilationByDefault() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "value": "text"
                    },
                    {
                      "result switch .value": {
                        "'text'": "matched",
                        "else": "fallback"
                      }
                    }
                  ],
                  "template": "{{ .result }}"
                }
                """;

        var rendered = JjtemplateSideMenuToolWindowFactory.compileSource(source, "{}");

        assertNull(rendered);
    }

    @Test
    void compileSourceResolvesFunctionMocksFromContextJson() throws Exception {
        var source = """
                {
                  "template": "{{ .context.do('hello') }}: {{ .context.do(42) }}"
                }
                """;
        var contextJson = """
                {
                  "context": {
                    "do": [
                      {
                        "in": ["hello"],
                        "out": "demo"
                      },
                      {
                        "in": 42,
                        "out": false
                      }
                    ]
                  }
                }
                """;

        var rendered = JjtemplateSideMenuToolWindowFactory.compileSource(source, contextJson);

        assertEquals("demo: false", rendered);
    }

    @Test
    void compileSourceMatchesCompleteFunctionArgumentList() throws Exception {
        var source = """
                {
                  "template": "{{ .service.combine('answer', 42) }}"
                }
                """;
        var contextJson = """
                {
                  "service": {
                    "name": "demo",
                    "combine": [
                      {
                        "in": ["answer", 42],
                        "out": {
                          "matched": true
                        }
                      }
                    ]
                  }
                }
                """;

        var rendered = JjtemplateSideMenuToolWindowFactory.compileSource(source, contextJson);

        assertEquals(Map.of("matched", true), rendered);
    }

    @Test
    void compileSourceReportsFunctionCallsWithoutMatchingMockCase() {
        var source = """
                {
                  "template": "{{ .context.do('missing') }}"
                }
                """;
        var contextJson = """
                {
                  "context": {
                    "do": [
                      {
                        "in": "known",
                        "out": "demo"
                      }
                    ]
                  }
                }
                """;

        var exception = assertThrows(
                Exception.class,
                () -> JjtemplateSideMenuToolWindowFactory.compileSource(source, contextJson)
        );

        assertTrue(hasMessage(exception,
                "No function mock case matched method 'do' with arguments [missing]"
        ));
    }

    private static boolean hasMessage(Throwable throwable, String expected) {
        var cursor = throwable;
        while (cursor != null) {
            if (cursor.getMessage() != null && cursor.getMessage().contains(expected)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    @Test
    void buildContextTemplateGeneratesStaticFunctionMockScenarios() throws Exception {
        var source = """
                {
                  "template": [
                    "{{ .context.do('hello') }}",
                    "{{ .context.do(42) }}",
                    "{{ .context.do('hello') }}",
                    "{{ .service.combine('answer', 42) }}",
                    "{{ .service.ping() }}"
                  ]
                }
                """;
        var expected = MAPPER.readTree("""
                {
                  "context": {
                    "do": [
                      {
                        "in": ["hello"],
                        "out": null
                      },
                      {
                        "in": [42],
                        "out": null
                      }
                    ]
                  },
                  "service": {
                    "combine": [
                      {
                        "in": ["answer", 42],
                        "out": null
                      }
                    ],
                    "ping": [
                      {
                        "in": [],
                        "out": null
                      }
                    ]
                  }
                }
                """);

        var generated = JjtemplateSideMenuToolWindowFactory.buildContextTemplate(source);

        assertEquals(expected.toString(), MAPPER.writeValueAsString(generated));
    }

    @Test
    void buildContextTemplateLeavesDynamicFunctionCallAsPlaceholder() throws Exception {
        var source = """
                {
                  "template": "{{ .service.find(.request.id) }}"
                }
                """;

        var generated = JjtemplateSideMenuToolWindowFactory.buildContextTemplate(source);

        var expected = MAPPER.readTree("""
                {
                  "service": {
                    "find": null
                  },
                  "request": {
                    "id": null
                  }
                }
                """);

        assertEquals(expected, MAPPER.valueToTree(generated));
    }

    @Test
    void buildContextTemplateSupportsWrappedDefinitionExpressions() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "{{ choice switch .request.kind }}": {
                        "{{ 'match' }}": "{{ .request.label }}",
                        "{{ else }}": "fallback"
                      }
                    },
                    {
                      "{{ items range item,index of .request.values }}": {
                        "value": "{{ .item }}",
                        "position": "{{ .index }}"
                      }
                    }
                  ],
                  "template": {
                    "choice": "{{ .choice }}",
                    "items": "{{ .items }}"
                  }
                }
                """;

        var generated = JjtemplateSideMenuToolWindowFactory.buildContextTemplate(source);

        var expected = MAPPER.readTree("""
                {
                  "request": {
                    "kind": null,
                    "label": null,
                    "values": null
                  }
                }
                """);
        assertEquals(expected, MAPPER.valueToTree(generated));
    }

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
    void optimizeSourceSupportsWrappedDefinitionExpressions() throws Exception {
        var source = """
                {
                  "definitions": [
                    {
                      "value": "text"
                    },
                    {
                      "{{ result switch .value }}": {
                        "{{ 'text' }}": "matched",
                        "{{ else }}": "fallback"
                      }
                    }
                  ],
                  "template": "{{ .result }}"
                }
                """;

        var optimized = JjtemplateSideMenuToolWindowFactory.optimizeSource(
                source,
                List.of(),
                2
        );
        var optimizedScript = MAPPER.readValue(optimized, TemplateScript.class);
        var rendered = TemplateCompiler.getInstance().compile(optimizedScript).render();

        assertEquals("matched", rendered);
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
