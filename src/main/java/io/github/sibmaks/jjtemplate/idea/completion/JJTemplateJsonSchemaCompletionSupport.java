package io.github.sibmaks.jjtemplate.idea.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.JsonLanguage;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

final class JJTemplateJsonSchemaCompletionSupport {
    private JJTemplateJsonSchemaCompletionSupport() {
    }

    static void addCompletions(CompletionParameters parameters, CompletionResultSet result) {
        var originalFile = parameters.getOriginalFile();
        var schema = JsonSchemaService.Impl.get(originalFile.getProject()).getSchemaObject(originalFile);
        if (schema == null) {
            return;
        }

        var originalSource = parameters.getEditor().getDocument().getText();
        var psiFactory = PsiFileFactory.getInstance(originalFile.getProject());
        var originalJsonPsi = psiFactory.createFileFromText(
                originalFile.getName() + ".json",
                JsonLanguage.INSTANCE,
                originalSource
        );
        if (!(originalJsonPsi instanceof JsonFile) || originalSource.isEmpty()) {
            return;
        }

        var caretOffset = parameters.getEditor().getCaretModel().getOffset();
        var originalOffset = Math.min(Math.max(caretOffset - 1, 0), originalSource.length() - 1);
        var position = originalJsonPsi.findElementAt(originalOffset);
        if (position == null) {
            return;
        }

        var stringLiteral = position instanceof JsonStringLiteral literal
                ? literal
                : PsiTreeUtil.getParentOfType(position, JsonStringLiteral.class, false);
        var schemaResult = result.withPrefixMatcher(completionPrefix(
                originalSource,
                caretOffset,
                stringLiteral
        ));
        if (stringLiteral != null && stringLiteral.isPropertyName()) {
            var property = PsiTreeUtil.getParentOfType(stringLiteral, JsonProperty.class, false);
            var object = property == null ? null : PsiTreeUtil.getParentOfType(property, JsonObject.class, true);
            addPropertyCompletions(originalFile.getProject(), schema, object, property, true, schemaResult);
        } else if (isObjectPropertyPosition(originalSource, caretOffset)
                && isInsideObject(originalSource, caretOffset)) {
            var object = position instanceof JsonObject jsonObject
                    ? jsonObject
                    : PsiTreeUtil.getParentOfType(position, JsonObject.class, false);
            addPropertyCompletions(originalFile.getProject(), schema, object, null, false, schemaResult);
        } else {
            var valueTarget = stringLiteral == null
                    ? findValueTarget(position, caretOffset)
                    : stringLiteral;
            addValueCompletions(
                    originalFile.getProject(),
                    schema,
                    valueTarget,
                    isInsideJsonString(originalSource, caretOffset),
                    schemaResult
            );
        }
    }

    private static void addPropertyCompletions(com.intellij.openapi.project.Project project,
                                               JsonSchemaObject rootSchema,
                                               JsonObject object,
                                               JsonProperty property,
                                               boolean insideQuotedName,
                                               CompletionResultSet result) {
        if (object == null) {
            return;
        }
        var existingNames = new LinkedHashSet<String>();
        for (var existing : object.getPropertyList()) {
            if (existing != property) {
                existingNames.add(existing.getName());
            }
        }
        var resolvedSchemas = resolveSchemas(project, rootSchema, object);
        for (var candidate : resolvedSchemas) {
            var names = candidate.getPropertyNames();
            while (names.hasNext()) {
                var name = names.next();
                var propertySchema = candidate.getPropertyByName(name);
                if (!existingNames.contains(name) && propertySchema != null) {
                    var lookup = LookupElementBuilder.create(name)
                            .withTypeText(propertyTypeText(propertySchema), true);
                    if (!insideQuotedName) {
                        lookup = lookup.withInsertHandler((context, item) -> {
                            var text = '"' + name + "\": ";
                            context.getDocument().replaceString(
                                    context.getStartOffset(),
                                    context.getTailOffset(),
                                    text
                            );
                            context.setTailOffset(context.getStartOffset() + text.length());
                        });
                    }
                    result.addElement(lookup);
                }
            }
        }
    }

    private static void addValueCompletions(com.intellij.openapi.project.Project project,
                                            JsonSchemaObject rootSchema,
                                            com.intellij.psi.PsiElement valueTarget,
                                            boolean insideString,
                                            CompletionResultSet result) {
        if (valueTarget == null) {
            return;
        }
        var value = valueTarget instanceof JsonProperty property ? property.getValue() : valueTarget;
        var stringValue = insideString || value instanceof JsonStringLiteral;
        var variants = new LinkedHashSet<Object>();
        var resolvedSchemas = value == null
                ? resolveSchemas(project, rootSchema, valueTarget)
                : resolveSchemas(project, rootSchema, value);
        for (var candidate : resolvedSchemas) {
            if (candidate.getEnum() != null) {
                variants.addAll(candidate.getEnum());
            }
            if (candidate.getDefault() != null) {
                variants.add(candidate.getDefault());
            }
            var type = candidate.getType();
            var typeVariants = candidate.getTypeVariants();
            if (type == JsonSchemaType._boolean
                    || typeVariants != null && typeVariants.contains(JsonSchemaType._boolean)) {
                variants.add(Boolean.TRUE);
                variants.add(Boolean.FALSE);
            }
            if (type == JsonSchemaType._null
                    || typeVariants != null && typeVariants.contains(JsonSchemaType._null)) {
                variants.add(null);
            }
        }
        for (var variant : variants) {
            var text = schemaValueText(variant, stringValue);
            if (text != null) {
                result.addElement(LookupElementBuilder.create(text).withTypeText("schema value", true));
            }
        }
    }

    private static Collection<JsonSchemaObject> resolveSchemas(com.intellij.openapi.project.Project project,
                                                               JsonSchemaObject rootSchema,
                                                               com.intellij.psi.PsiElement element) {
        JsonPointerPosition pointer;
        com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter adapter = null;
        if (element instanceof JsonValue value) {
            pointer = jsonPointer(value);
            adapter = JsonOriginalPsiWalker.INSTANCE.createValueAdapter(element);
        } else if (element instanceof JsonProperty property && property.getParent() instanceof JsonObject object) {
            pointer = jsonPointer(object);
            pointer.addFollowingStep(property.getName());
        } else {
            return java.util.List.of();
        }
        return new JsonSchemaResolver(
                project,
                rootSchema,
                pointer,
                new JsonSchemaNodeExpansionRequest(adapter, false)
        ).resolve();
    }

    private static JsonPointerPosition jsonPointer(JsonValue value) {
        var reversedSteps = new ArrayList<Object>();
        com.intellij.psi.PsiElement current = value;
        while (true) {
            var parent = current.getParent();
            if (parent instanceof JsonProperty property) {
                reversedSteps.add(property.getName());
                current = property.getParent();
            } else if (parent instanceof JsonArray array && current instanceof JsonValue arrayValue) {
                reversedSteps.add(array.getValueList().indexOf(arrayValue));
                current = array;
            } else {
                break;
            }
        }
        var pointer = new JsonPointerPosition();
        for (int i = reversedSteps.size() - 1; i >= 0; i--) {
            var step = reversedSteps.get(i);
            if (step instanceof String name) {
                pointer.addFollowingStep(name);
            } else {
                pointer.addFollowingStep((Integer) step);
            }
        }
        return pointer;
    }

    private static String propertyTypeText(JsonSchemaObject schema) {
        var description = schema.getDescription();
        if (description != null && !description.isBlank()) {
            return description;
        }
        var type = schema.getType();
        return type == null ? "schema property" : type.getDescription();
    }

    private static String schemaValueText(Object value, boolean insideString) {
        if (value == null) {
            return insideString ? null : "null";
        }
        if (value instanceof String text) {
            var quoted = text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"';
            if (insideString) {
                return quoted ? text.substring(1, text.length() - 1) : text;
            }
            return quoted ? text : '"' + text + '"';
        }
        return value.toString();
    }

    private static String completionPrefix(String source, int offset, JsonStringLiteral stringLiteral) {
        if (stringLiteral != null) {
            var start = Math.min(stringLiteral.getTextRange().getStartOffset() + 1, offset);
            return source.substring(start, Math.min(offset, source.length()));
        }
        var start = Math.min(offset, source.length());
        while (start > 0) {
            var ch = source.charAt(start - 1);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '+' && ch != '.') {
                break;
            }
            start--;
        }
        return source.substring(start, Math.min(offset, source.length()));
    }

    private static boolean isInsideJsonString(String source, int offset) {
        var inString = false;
        var escaped = false;
        for (int i = 0; i < Math.min(offset, source.length()); i++) {
            var ch = source.charAt(i);
            if (!inString) {
                if (ch == '"') {
                    inString = true;
                }
            } else if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                inString = false;
            }
        }
        return inString;
    }

    private static boolean isObjectPropertyPosition(String source, int offset) {
        var index = Math.min(offset, source.length()) - 1;
        while (index >= 0 && (Character.isWhitespace(source.charAt(index))
                || Character.isLetterOrDigit(source.charAt(index))
                || source.charAt(index) == '_' || source.charAt(index) == '-')) {
            index--;
        }
        return index >= 0 && (source.charAt(index) == '{' || source.charAt(index) == ',');
    }

    private static com.intellij.psi.PsiElement findValueTarget(com.intellij.psi.PsiElement position, int caretOffset) {
        if (position instanceof JsonValue) {
            return position;
        }
        var value = PsiTreeUtil.getParentOfType(position, JsonValue.class, false);
        if (value != null && !(value instanceof JsonObject) && !(value instanceof JsonArray)) {
            return value;
        }
        var property = position instanceof JsonProperty jsonProperty
                ? jsonProperty
                : PsiTreeUtil.getParentOfType(position, JsonProperty.class, false);
        if (property != null) {
            return property;
        }
        var object = position instanceof JsonObject jsonObject
                ? jsonObject
                : PsiTreeUtil.getParentOfType(position, JsonObject.class, false);
        if (object == null) {
            return null;
        }
        JsonProperty incomplete = null;
        for (var candidate : object.getPropertyList()) {
            if (candidate.getValue() == null && candidate.getTextRange().getEndOffset() <= caretOffset) {
                incomplete = candidate;
            }
        }
        return incomplete;
    }

    private static boolean isInsideObject(String source, int offset) {
        var containers = new ArrayDeque<Character>();
        var inString = false;
        var escaped = false;
        for (int i = 0; i < Math.min(offset, source.length()); i++) {
            var ch = source.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                containers.addLast(ch);
            } else if ((ch == '}' || ch == ']') && !containers.isEmpty()) {
                containers.removeLast();
            }
        }
        return !containers.isEmpty() && containers.getLast() == '{';
    }
}
