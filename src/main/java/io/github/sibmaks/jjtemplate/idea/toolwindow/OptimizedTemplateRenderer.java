package io.github.sibmaks.jjtemplate.idea.toolwindow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sibmaks.jjtemplate.compiler.impl.CompiledTemplateImpl;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.ConstantTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.PipeChainTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.RangeTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.TemplateConcatTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.TemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.TernaryTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.VariableTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.function.ConstantFunctionCallTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.function.DynamicFunctionCallTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.function.FunctionCallTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.list.ConditionListElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.list.DynamicListElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.list.ListStaticItemElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.list.ListTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.list.SpreadListElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.object.ObjectFieldElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.object.ObjectStaticFieldElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.object.ObjectTemplateExpression;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.object.SpreadObjectElement;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.switch_case.ConstantSwitchCase;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.switch_case.ElseSwitchCase;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.switch_case.ExpressionSwitchCase;
import io.github.sibmaks.jjtemplate.compiler.runtime.expression.switch_case.SwitchTemplateExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class OptimizedTemplateRenderer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OptimizedTemplateRenderer() {
    }

    static @NotNull Map<String, Object> render(@NotNull CompiledTemplateImpl compiled) {
        var script = new LinkedHashMap<String, Object>();
        var definition = new LinkedHashMap<String, Object>();
        for (var variable : compiled.getInternalVariables()) {
            addField(definition, variable);
        }
        script.put("definitions", definition.isEmpty() ? List.of() : List.of(definition));
        script.put("template", renderValue(compiled.getCompiledTemplate()));
        return script;
    }

    private static Object renderValue(TemplateExpression expression) {
        if (expression instanceof ConstantTemplateExpression constant) {
            return constant.getValue();
        }
        if (expression instanceof ObjectTemplateExpression object) {
            return renderObject(object);
        }
        if (expression instanceof ListTemplateExpression list) {
            return renderList(list);
        }
        if (expression instanceof TemplateConcatTemplateExpression concat) {
            return renderConcat(concat);
        }
        return interpolation(renderSource(expression));
    }

    private static Map<String, Object> renderObject(ObjectTemplateExpression expression) {
        var object = new LinkedHashMap<String, Object>();
        for (var element : expression.getElements()) {
            if (element instanceof ObjectStaticFieldElement field) {
                object.put(field.getKey(), field.getValue());
            } else if (element instanceof ObjectFieldElement field) {
                addField(object, field);
            } else if (element instanceof SpreadObjectElement spread) {
                object.put(spreadInterpolation(renderSource(spread.getSource())), true);
            } else {
                throw unsupported(element);
            }
        }
        return object;
    }

    private static void addField(Map<String, Object> target, ObjectFieldElement field) {
        if (field.getValue() instanceof RangeTemplateExpression range) {
            target.put(rangeDeclaration(range), renderValue(range.getBody()));
            return;
        }
        if (field.getValue() instanceof SwitchTemplateExpression switchExpression) {
            target.put(
                    switchDeclaration(renderSource(field.getKey()), switchExpression),
                    renderSwitchCases(switchExpression)
            );
            return;
        }
        target.put(renderKey(field.getKey()), renderValue(field.getValue()));
    }

    private static String renderKey(TemplateExpression key) {
        if (key instanceof ConstantTemplateExpression constant) {
            return String.valueOf(constant.getValue());
        }
        if (key instanceof TemplateConcatTemplateExpression concat) {
            return renderConcat(concat);
        }
        return interpolation(renderSource(key));
    }

    private static List<Object> renderList(ListTemplateExpression expression) {
        var list = new ArrayList<>();
        for (var element : expression.getElements()) {
            if (element instanceof ListStaticItemElement item) {
                list.add(item.getValue());
            } else if (element instanceof DynamicListElement item) {
                list.add(renderValue(item.getValue()));
            } else if (element instanceof ConditionListElement condition) {
                list.add(conditionInterpolation(renderSource(condition.getSource())));
            } else if (element instanceof SpreadListElement spread) {
                list.add(spreadInterpolation(renderSource(spread.getSource())));
            } else {
                throw unsupported(element);
            }
        }
        return list;
    }

    private static String renderConcat(TemplateConcatTemplateExpression expression) {
        var result = new StringBuilder();
        for (var item : expression.getExpressions()) {
            if (item instanceof ConstantTemplateExpression constant) {
                result.append(constant.getValue());
            } else {
                result.append(interpolation(renderSource(item)));
            }
        }
        return result.toString();
    }

    private static String renderSource(TemplateExpression expression) {
        if (expression instanceof ConstantTemplateExpression constant) {
            return renderLiteral(constant.getValue());
        }
        if (expression instanceof VariableTemplateExpression variable) {
            return variable.getSourceExpression();
        }
        if (expression instanceof ConstantFunctionCallTemplateExpression function) {
            var arguments = function.getArguments().stream()
                    .map(OptimizedTemplateRenderer::renderLiteral)
                    .collect(Collectors.joining(", "));
            return functionCall(function, arguments);
        }
        if (expression instanceof DynamicFunctionCallTemplateExpression function) {
            var arguments = renderArguments(function.getArgExpression());
            return functionCall(function, arguments);
        }
        if (expression instanceof PipeChainTemplateExpression pipe) {
            var result = new StringBuilder(renderSource(pipe.getRoot()));
            for (var function : pipe.getChain()) {
                result.append(" | ").append(functionCall(function, renderFunctionArguments(function)));
            }
            return result.toString();
        }
        if (expression instanceof TernaryTemplateExpression ternary) {
            return "(" + renderSource(ternary.getCondition()) + " ? "
                    + renderSource(ternary.getThenTrue()) + " : "
                    + renderSource(ternary.getThenFalse()) + ")";
        }
        throw unsupported(expression);
    }

    private static String renderFunctionArguments(FunctionCallTemplateExpression function) {
        if (function instanceof ConstantFunctionCallTemplateExpression constant) {
            return constant.getArguments().stream()
                    .map(OptimizedTemplateRenderer::renderLiteral)
                    .collect(Collectors.joining(", "));
        }
        if (function instanceof DynamicFunctionCallTemplateExpression dynamic) {
            return renderArguments(dynamic.getArgExpression());
        }
        throw unsupported(function);
    }

    private static String renderArguments(ListTemplateExpression arguments) {
        var rendered = new ArrayList<String>();
        for (var element : arguments.getElements()) {
            if (element instanceof ListStaticItemElement item) {
                rendered.add(renderLiteral(item.getValue()));
            } else if (element instanceof DynamicListElement item) {
                rendered.add(renderSource(item.getValue()));
            } else if (element instanceof SpreadListElement spread) {
                rendered.add("...(" + renderSource(spread.getSource()) + ")");
            } else {
                throw unsupported(element);
            }
        }
        return String.join(", ", rendered);
    }

    private static String functionCall(FunctionCallTemplateExpression expression, String arguments) {
        var function = expression.getFunction();
        var namespace = function.getNamespace();
        var name = namespace == null || namespace.isEmpty()
                ? function.getName()
                : namespace + ":" + function.getName();
        return arguments.isEmpty() ? name : name + " " + arguments;
    }

    private static String rangeDeclaration(RangeTemplateExpression range) {
        return interpolation(
                renderSource(range.getName())
                        + " range " + range.getFirstVariableName()
                        + "," + range.getSecondVariableName()
                        + " of " + renderSource(range.getSource())
        );
    }

    private static String switchDeclaration(String name, SwitchTemplateExpression expression) {
        return interpolation(name + " switch " + renderSource(expression.getCondition()));
    }

    private static Map<String, Object> renderSwitchCases(SwitchTemplateExpression expression) {
        var cases = new LinkedHashMap<String, Object>();
        for (var switchCase : expression.getCases()) {
            if (switchCase instanceof ConstantSwitchCase constant) {
                addSwitchCase(cases, renderLiteral(constant.getConstant()), constant.getValue());
            } else if (switchCase instanceof ExpressionSwitchCase dynamic) {
                addSwitchCase(cases, renderSource(dynamic.getKey()), dynamic.getValue());
            } else if (switchCase instanceof ElseSwitchCase otherwise) {
                addSwitchCase(cases, "else", otherwise.getValue());
            } else {
                throw unsupported(switchCase);
            }
        }
        return cases;
    }

    private static void addSwitchCase(Map<String, Object> cases, String key, TemplateExpression value) {
        if (value instanceof SwitchTemplateExpression nested) {
            cases.put(switchDeclaration(key, nested), renderSwitchCases(nested));
            return;
        }
        cases.put(interpolation(key), renderValue(value));
    }

    private static String renderLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return "'" + string
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "'";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot render template literal", exception);
        }
    }

    private static String interpolation(String source) {
        return "{{ " + source + " }}";
    }

    private static String conditionInterpolation(String source) {
        return "{{? " + source + " }}";
    }

    private static String spreadInterpolation(String source) {
        return "{{. " + source + " }}";
    }

    private static IllegalArgumentException unsupported(Object value) {
        return new IllegalArgumentException("Unsupported optimized template node: " + value.getClass().getName());
    }
}
