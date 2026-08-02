package io.github.sibmaks.jjtemplate.idea.lang;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import io.github.sibmaks.jjtemplate.lexer.api.TemplateLexerException;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class JJTemplateAnnotator implements Annotator {
    private static final TextAttributes GLOBAL_VARIABLE_STRIPE_ATTRIBUTES = createGlobalVariableStripeAttributes();

    private static void highlightJsonLike(
            String text,
            AnnotationHolder holder,
            List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions
    ) {
        var rangeBindings = collectRangeBindings(text);
        var nesting = 0;
        var inString = false;
        var escaped = false;
        var stringStart = -1;

        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                    if (isObjectKey(text, i + 1)) {
                        highlightStringWithLookups(
                                text,
                                stringStart,
                                i + 1,
                                holder,
                                JJTemplateSyntaxHighlighter.OBJECT_KEY,
                                localDefinitions,
                                rangeBindings
                        );
                    } else {
                        highlightStringWithLookups(
                                text,
                                stringStart,
                                i + 1,
                                holder,
                                JJTemplateSyntaxHighlighter.JSON_STRING,
                                localDefinitions,
                                rangeBindings
                        );
                    }
                }
                continue;
            }

            if (TemplateTextScanner.isTemplateStart(text, i)) {
                var templateEnd = TemplateTextScanner.findTemplateEnd(text, i, text.length());
                if (templateEnd < 0) {
                    break;
                }
                i = templateEnd - 1;
                continue;
            }

            if (ch == '"') {
                inString = true;
                stringStart = i;
                continue;
            }
            if (ch == '{') {
                if (nesting == 0) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.from(i, 1))
                            .textAttributes(JJTemplateSyntaxHighlighter.ROOT_OBJECT)
                            .create();
                }
                nesting++;
                continue;
            }
            if (ch == '[') {
                if (nesting == 0) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.from(i, 1))
                            .textAttributes(JJTemplateSyntaxHighlighter.ROOT_ARRAY)
                            .create();
                }
                nesting++;
                continue;
            }
            if (ch == '}' || ch == ']') {
                if (nesting > 0) {
                    nesting--;
                }
                if (nesting == 0) {
                    var attributes = ch == '}' ? JJTemplateSyntaxHighlighter.ROOT_OBJECT : JJTemplateSyntaxHighlighter.ROOT_ARRAY;
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.from(i, 1))
                            .textAttributes(attributes)
                            .create();
                }
                continue;
            }

            if (ch == '-' || Character.isDigit(ch)) {
                var end = readNumberEnd(text, i);
                if (end > i) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.create(i, end))
                            .textAttributes(JJTemplateSyntaxHighlighter.JSON_NUMBER)
                            .create();
                    i = end - 1;
                    continue;
                }
            }

            if (Character.isLetter(ch)) {
                var end = readWordEnd(text, i);
                var word = text.substring(i, end);
                if ("true".equals(word) || "false".equals(word) || "null".equals(word)) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(TextRange.create(i, end))
                            .textAttributes(JJTemplateSyntaxHighlighter.JSON_BOOLEAN)
                            .create();
                }
                i = end - 1;
            }
        }
    }

    private static boolean isObjectKey(String text, int index) {
        var pointer = index;
        while (pointer < text.length() && Character.isWhitespace(text.charAt(pointer))) {
            pointer++;
        }
        return pointer < text.length() && text.charAt(pointer) == ':';
    }

    private static void highlightStringWithLookups(String text,
                                                   int start,
                                                   int endExclusive,
                                                   AnnotationHolder holder,
                                                   TextAttributesKey plainTextStyle,
                                                   List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
                                                   Set<String> rangeBindings) {
        var contentStart = start + 1;
        var contentEnd = Math.max(contentStart, endExclusive - 1);
        var cursor = start;
        var index = contentStart;

        while (index < contentEnd) {
            if (!TemplateTextScanner.isTemplateStart(text, index)) {
                index++;
                continue;
            }
            var lookupEnd = TemplateTextScanner.findTemplateEnd(text, index, contentEnd);
            if (lookupEnd < 0) {
                break;
            }
            annotateRange(holder, cursor, index, plainTextStyle);
            highlightLookupTokens(
                    text.substring(index, lookupEnd),
                    index,
                    holder,
                    plainTextStyle,
                    localDefinitions,
                    rangeBindings
            );
            cursor = lookupEnd;
            index = lookupEnd;
        }
        annotateRange(holder, cursor, endExclusive, plainTextStyle);
    }

    private static void highlightLookupTokens(String lookup,
                                              int baseOffset,
                                              AnnotationHolder holder,
                                              TextAttributesKey fallbackStyle,
                                              List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
                                              Set<String> inheritedRangeBindings) {
        try {
            var tokens = new TemplateLexer(lookup).tokens();
            highlightLookupTokenList(
                    tokens,
                    lookup,
                    baseOffset,
                    holder,
                    fallbackStyle,
                    localDefinitions,
                    inheritedRangeBindings
            );
        } catch (Exception ignored) {
            var normalizedTokens = tryLexWithEscapedApostropheNormalization(lookup);
            if (normalizedTokens != null) {
                highlightLookupTokenList(
                        normalizedTokens,
                        lookup,
                        baseOffset,
                        holder,
                        fallbackStyle,
                        localDefinitions,
                        inheritedRangeBindings
                );
                return;
            }
            highlightLookupFallback(lookup, baseOffset, holder, fallbackStyle);
        }
    }

    private static void highlightLookupTokenList(List<Token> tokens,
                                                 String lookupSource,
                                                 int baseOffset,
                                                 AnnotationHolder holder,
                                                 TextAttributesKey fallbackStyle,
                                                 List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
                                                 Set<String> inheritedRangeBindings) {
        var rangeBindings = new HashSet<>(inheritedRangeBindings);
        rangeBindings.addAll(collectRangeBindings(tokens));
        for (var token : tokens) {
            if (token.type == TokenType.STRING) {
                annotateRange(holder, baseOffset + token.start, baseOffset + token.end, JJTemplateSyntaxHighlighter.JSON_STRING);
                highlightNestedLookupTokensInString(
                        lookupSource,
                        token.start,
                        token.end,
                        baseOffset,
                        holder,
                        localDefinitions,
                        rangeBindings
                );
                continue;
            }
            var key = mapLookupToken(token.type, fallbackStyle);
            if (key == null) {
                continue;
            }
            annotateRange(holder, baseOffset + token.start, baseOffset + token.end, key);
        }
        highlightTemplateIdentifiers(tokens, baseOffset, holder, localDefinitions, rangeBindings, false);
    }

    private static List<Token> tryLexWithEscapedApostropheNormalization(String lookup) {
        if (!lookup.contains("\\\\'")) {
            return null;
        }
        try {
            var normalized = normalizeEscapedApostrophes(lookup);
            if (lookup.equals(normalized.source())) {
                return null;
            }
            var normalizedTokens = new TemplateLexer(normalized.source()).tokens();
            return remapTokensToOriginalOffsets(normalizedTokens, normalized.boundaries());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Token> remapTokensToOriginalOffsets(List<Token> tokens, int[] boundaries) {
        var remapped = new java.util.ArrayList<Token>(tokens.size());
        var maxBoundary = boundaries.length - 1;
        for (var token : tokens) {
            var start = boundaries[Math.clamp(token.start, 0, maxBoundary)];
            var end = boundaries[Math.clamp(token.end, 0, maxBoundary)];
            remapped.add(new Token(token.type, token.lexeme, start, end));
        }
        return remapped;
    }

    private static NormalizedLookup normalizeEscapedApostrophes(String source) {
        var normalized = new StringBuilder(source.length());
        var boundaries = new int[source.length() + 1];
        boundaries[0] = 0;
        var normalizedLength = 0;

        for (int i = 0; i < source.length(); ) {
            if (i + 2 < source.length()
                    && source.charAt(i) == '\\'
                    && source.charAt(i + 1) == '\\'
                    && source.charAt(i + 2) == '\'') {
                normalized.append('\\');
                boundaries[++normalizedLength] = i + 1;
                normalized.append('\'');
                boundaries[++normalizedLength] = i + 3;
                i += 3;
                continue;
            }
            normalized.append(source.charAt(i));
            boundaries[++normalizedLength] = i + 1;
            i++;
        }

        return new NormalizedLookup(normalized.toString(), Arrays.copyOf(boundaries, normalizedLength + 1));
    }

    private static void highlightNestedLookupTokensInString(String lookup,
                                                            int stringTokenStart,
                                                            int stringTokenEnd,
                                                            int baseOffset,
                                                            AnnotationHolder holder,
                                                            List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
                                                            Set<String> rangeBindings) {
        if (stringTokenEnd - stringTokenStart < 2) {
            return;
        }
        var tokenSource = lookup.substring(stringTokenStart, stringTokenEnd);
        var length = tokenSource.length();
        var contentStart = 0;
        var contentEnd = length;
        if (length >= 2) {
            var quote = tokenSource.charAt(0);
            if ((quote == '\'' || quote == '"') && tokenSource.charAt(length - 1) == quote) {
                contentStart = 1;
                contentEnd = length - 1;
            }
        }
        for (int i = contentStart; i < contentEnd; i++) {
            if (!TemplateTextScanner.isTemplateStart(tokenSource, i)) {
                continue;
            }
            var nestedEnd = TemplateTextScanner.findTemplateEnd(tokenSource, i, contentEnd);
            if (nestedEnd < 0) {
                break;
            }
            highlightLookupTokens(
                    tokenSource.substring(i, nestedEnd),
                    baseOffset + stringTokenStart + i,
                    holder,
                    JJTemplateSyntaxHighlighter.JSON_STRING,
                    localDefinitions,
                    rangeBindings
            );
            i = nestedEnd - 1;
        }
    }

    private static TextAttributesKey mapLookupToken(TokenType tokenType, TextAttributesKey fallbackStyle) {
        return switch (tokenType) {
            case OPEN_EXPR, OPEN_COND, OPEN_SPREAD, CLOSE -> DefaultLanguageHighlighterColors.BRACES;
            case KEYWORD, BOOLEAN, NULL -> DefaultLanguageHighlighterColors.KEYWORD;
            case NUMBER -> DefaultLanguageHighlighterColors.NUMBER;
            case PIPE, COLON, QUESTION -> DefaultLanguageHighlighterColors.OPERATION_SIGN;
            case DOT -> DefaultLanguageHighlighterColors.DOT;
            case COMMA -> DefaultLanguageHighlighterColors.COMMA;
            case LPAREN, RPAREN -> DefaultLanguageHighlighterColors.PARENTHESES;
            case STRING -> JJTemplateSyntaxHighlighter.JSON_STRING;
            case IDENT, TEXT -> null;
        };
    }

    private static void highlightLookupFallback(String lookup,
                                                int baseOffset,
                                                AnnotationHolder holder,
                                                TextAttributesKey fallbackStyle) {
        var length = lookup.length();
        if (length >= 2) {
            annotateRange(holder, baseOffset, baseOffset + 2, DefaultLanguageHighlighterColors.BRACES);
        }
        if (length > 4) {
            annotateRange(holder, baseOffset + 2, baseOffset + length - 2, fallbackStyle);
        }
        if (length >= 4) {
            annotateRange(holder, baseOffset + length - 2, baseOffset + length, DefaultLanguageHighlighterColors.BRACES);
        } else if (length > 2) {
            annotateRange(holder, baseOffset + 2, baseOffset + length, fallbackStyle);
        }
    }

    private static void validateJson(String text, AnnotationHolder holder) {
        if (text.isBlank()) {
            return;
        }
        try (var parser = new JsonFactory().createParser(text)) {
            var token = parser.nextToken();
            while (token != null) {
                token = parser.nextToken();
            }
        } catch (JsonParseException e) {
            var offset = toOffset(text, e);
            if (isInsideTemplate(text, offset) && !isInvalidJsonEscape(e)) {
                return;
            }
            holder.newAnnotation(HighlightSeverity.ERROR, e.getOriginalMessage())
                    .range(TextRange.from(offset, 1))
                    .create();
        } catch (Exception ignored) {
            // Do not block editing if JSON parser is unavailable in IDE runtime.
        }
    }

    private static boolean isInsideTemplate(String text, int offset) {
        if (text.isEmpty()) {
            return false;
        }
        var target = Math.clamp(offset, 0, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            if (TemplateTextScanner.isTemplateStart(text, i)) {
                var templateEnd = TemplateTextScanner.findTemplateEnd(text, i, text.length());
                if (templateEnd < 0) {
                    return target >= i;
                }
                if (target >= i && target < templateEnd) {
                    return true;
                }
                i = templateEnd - 1;
                continue;
            }
            if (i == target) {
                return false;
            }
        }
        return false;
    }

    private static boolean isInvalidJsonEscape(JsonParseException error) {
        var message = error.getOriginalMessage();
        if (message == null) {
            return false;
        }
        return message.contains("character escape")
                || message.contains("Unexpected character ('\\\\'");
    }

    private static void validateSubstitutions(List<Token> tokens, AnnotationHolder holder) {
        validateSubstitutions(tokens, 0, holder);
    }

    private static void validateSubstitutions(List<Token> tokens, int baseOffset, AnnotationHolder holder) {
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.PIPE) {
                continue;
            }
            var next = findNextNonTextToken(tokens, i + 1);
            if (next == null || !isValidTokenAfterPipe(next.type())) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Missing expression after pipe operator")
                        .range(TextRange.create(baseOffset + token.start, baseOffset + Math.max(token.end, token.start + 1)))
                        .create();
            }
        }
    }

    private static boolean isValidTokenAfterPipe(TokenType type) {
        return switch (type) {
            case IDENT, STRING, NUMBER, BOOLEAN, NULL, LPAREN, DOT, KEYWORD -> true;
            default -> false;
        };
    }

    private static boolean isDefinitionName(List<Token> tokens, int identIndex) {
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next == null || next.type() != TokenType.KEYWORD) {
            return false;
        }
        return Keyword.SWITCH.eq(next.token().lexeme) || Keyword.RANGE.eq(next.token().lexeme);
    }

    private static boolean isRangeBindingName(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous == null) {
            return false;
        }
        if (previous.type() == TokenType.KEYWORD && Keyword.RANGE.eq(previous.token().lexeme)) {
            return true;
        }
        if (previous.type() != TokenType.COMMA) {
            return false;
        }
        var beforeComma = findPreviousNonTextToken(tokens, previous.index() - 1);
        if (beforeComma == null || beforeComma.type() != TokenType.IDENT) {
            return false;
        }
        var beforeFirstBinding = findPreviousNonTextToken(tokens, beforeComma.index() - 1);
        return beforeFirstBinding != null
                && beforeFirstBinding.type() == TokenType.KEYWORD
                && Keyword.RANGE.eq(beforeFirstBinding.token().lexeme);
    }

    private static boolean isFunctionCall(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous != null && previous.type() == TokenType.PIPE) {
            return true;
        }
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next != null && next.type() == TokenType.LPAREN) {
            return true;
        }
        if (next != null && next.type() == TokenType.COLON) {
            if (isNamespacedFunctionStart(tokens, identIndex)) {
                return true;
            }
            var nextNext = findNextNonTextToken(tokens, next.index() + 1);
            return nextNext != null && nextNext.type() == TokenType.COLON;
        }
        if (previous != null && previous.type() == TokenType.COLON) {
            if (isNamespacedFunctionName(tokens, identIndex)) {
                return true;
            }
            var previousPrevious = findPreviousNonTextToken(tokens, previous.index() - 1);
            if (previousPrevious != null && previousPrevious.type() == TokenType.COLON) {
                return isPipedIdentifier(tokens, previousPrevious.index() - 1);
            }
            if (previousPrevious != null && previousPrevious.type() == TokenType.IDENT) {
                return isPipedIdentifier(tokens, previousPrevious.index());
            }
        }
        return isNamespacedFunctionPrefix(tokens, identIndex) || isBareFunctionCall(tokens, identIndex);
    }

    private static boolean isNamespacedFunctionStart(List<Token> tokens, int identIndex) {
        var token = tokens.get(identIndex);
        if (token.type != TokenType.IDENT) {
            return false;
        }
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (!isFunctionBoundary(previous)) {
            return false;
        }
        var firstColon = findNextNonTextToken(tokens, identIndex + 1);
        if (firstColon == null || firstColon.type() != TokenType.COLON) {
            return false;
        }
        var afterFirstColon = findNextNonTextToken(tokens, firstColon.index() + 1);
        if (afterFirstColon == null) {
            return false;
        }
        if (afterFirstColon.type() == TokenType.IDENT) {
            return true;
        }
        if (afterFirstColon.type() != TokenType.COLON) {
            return false;
        }
        var functionName = findNextNonTextToken(tokens, afterFirstColon.index() + 1);
        return functionName != null && functionName.type() == TokenType.IDENT;
    }

    private static boolean isNamespacedFunctionName(List<Token> tokens, int identIndex) {
        var token = tokens.get(identIndex);
        if (token.type != TokenType.IDENT) {
            return false;
        }
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous == null || previous.type() != TokenType.COLON) {
            return false;
        }
        var namespace = findPreviousNonTextToken(tokens, previous.index() - 1);
        if (namespace == null || namespace.type() != TokenType.IDENT) {
            return false;
        }
        var beforeNamespace = findPreviousNonTextToken(tokens, namespace.index() - 1);
        return isFunctionBoundary(beforeNamespace);
    }

    private static boolean isBareFunctionCall(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous != null && (previous.type() == TokenType.DOT || previous.type() == TokenType.COLON
                || previous.type() == TokenType.PIPE || previous.type() == TokenType.IDENT)) {
            return false;
        }
        if (!isFunctionBoundary(previous)) {
            return false;
        }
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next == null) {
            return false;
        }
        return switch (next.type()) {
            case IDENT, STRING, NUMBER, BOOLEAN, NULL, LPAREN, DOT -> true;
            default -> false;
        };
    }

    private static boolean isFunctionBoundary(IndexedToken previous) {
        if (previous == null) {
            return true;
        }
        return previous.type() == TokenType.OPEN_EXPR
                || previous.type() == TokenType.OPEN_COND
                || previous.type() == TokenType.OPEN_SPREAD
                || previous.type() == TokenType.COMMA
                || previous.type() == TokenType.LPAREN
                || previous.type() == TokenType.KEYWORD
                || previous.type() == TokenType.PIPE;
    }

    private static boolean isNamespacedFunctionPrefix(List<Token> tokens, int identIndex) {
        var next = findNextNonTextToken(tokens, identIndex + 1);
        if (next == null || next.type() != TokenType.COLON) {
            return false;
        }
        var afterFirstColon = findNextNonTextToken(tokens, next.index() + 1);
        if (afterFirstColon == null) {
            return false;
        }
        if (afterFirstColon.type() == TokenType.IDENT) {
            return isPipedIdentifier(tokens, identIndex);
        }
        if (afterFirstColon.type() != TokenType.COLON) {
            return false;
        }
        var functionName = findNextNonTextToken(tokens, afterFirstColon.index() + 1);
        return functionName != null
                && functionName.type() == TokenType.IDENT
                && isPipedIdentifier(tokens, identIndex);
    }

    private static boolean isPipedIdentifier(List<Token> tokens, int identIndex) {
        if (identIndex < 0 || identIndex >= tokens.size()) {
            return false;
        }
        if (tokens.get(identIndex).type != TokenType.IDENT) {
            return false;
        }
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        return previous != null && previous.type() == TokenType.PIPE;
    }

    private static boolean isContextVariable(List<Token> tokens,
                                             int identIndex,
                                             boolean localDefinition,
                                             Set<String> rangeBindings) {
        var token = tokens.get(identIndex);
        if (!isRootVariable(tokens, identIndex)) {
            return false;
        }
        if (localDefinition) {
            return false;
        }
        return !rangeBindings.contains(token.lexeme);
    }

    private static boolean isRootVariable(List<Token> tokens, int identIndex) {
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous == null || previous.type() != TokenType.DOT) {
            return false;
        }
        var beforeDot = findPreviousNonTextToken(tokens, previous.index() - 1);
        if (beforeDot == null || beforeDot.type() != TokenType.IDENT) {
            return true;
        }
        return isFunctionCall(tokens, beforeDot.index());
    }

    private static Set<String> collectRangeBindings(List<Token> tokens) {
        var bindings = new HashSet<String>();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.KEYWORD || !Keyword.RANGE.eq(token.lexeme)) {
                continue;
            }
            var firstBinding = findNextNonTextToken(tokens, i + 1);
            if (firstBinding == null || firstBinding.type() != TokenType.IDENT) {
                continue;
            }
            bindings.add(firstBinding.token().lexeme);

            var maybeComma = findNextNonTextToken(tokens, firstBinding.index() + 1);
            if (maybeComma == null || maybeComma.type() != TokenType.COMMA) {
                continue;
            }
            var secondBinding = findNextNonTextToken(tokens, maybeComma.index() + 1);
            if (secondBinding != null && secondBinding.type() == TokenType.IDENT) {
                bindings.add(secondBinding.token().lexeme);
            }
        }
        return bindings;
    }

    private static Set<String> collectRangeBindings(String source) {
        try {
            return collectRangeBindings(new TemplateLexer(source).tokens());
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static IndexedToken findPreviousNonTextToken(List<Token> tokens, int from) {
        for (int i = from; i >= 0; i--) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return new IndexedToken(i, tokens.get(i));
            }
        }
        return null;
    }

    private static IndexedToken findNextNonTextToken(List<Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return new IndexedToken(i, tokens.get(i));
            }
        }
        return null;
    }

    private static int toOffset(String text, JsonParseException error) {
        var location = error.getLocation();
        if (location != null && location.getCharOffset() >= 0) {
            var offset = (int) location.getCharOffset();
            return Math.min(Math.max(offset, 0), Math.max(text.length() - 1, 0));
        }
        if (location == null || location.getLineNr() <= 0 || location.getColumnNr() <= 0) {
            return 0;
        }
        var line = 1;
        var index = 0;
        while (index < text.length() && line < location.getLineNr()) {
            if (text.charAt(index) == '\n') {
                line++;
            }
            index++;
        }
        var offset = index + location.getColumnNr() - 1;
        return Math.min(Math.max(offset, 0), Math.max(text.length() - 1, 0));
    }

    private static void annotateRange(AnnotationHolder holder,
                                      int start,
                                      int endExclusive,
                                      TextAttributesKey key) {
        if (start >= endExclusive) {
            return;
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange.create(start, endExclusive))
                .textAttributes(key)
                .create();
    }

    private static int readWordEnd(String text, int from) {
        var index = from;
        while (index < text.length() && Character.isLetter(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int readNumberEnd(String text, int from) {
        var index = from;
        if (text.charAt(index) == '-') {
            index++;
        }
        var intStart = index;
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
            index++;
        }
        if (intStart == index) {
            return from;
        }
        if (index < text.length() && text.charAt(index) == '.') {
            var dot = index++;
            var fractionStart = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (fractionStart == index) {
                return dot;
            }
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            var expStart = index++;
            if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            var digitsStart = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (digitsStart == index) {
                return expStart;
            }
        }
        return index;
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof JJTemplateFile)) {
            return;
        }
        var text = element.getText();
        var localDefinitions = UnusedDefinitionAnalyzer.findDefinitions(text);
        highlightJsonLike(text, holder, localDefinitions);
        validateJson(text, holder);
        try {
            var tokens = new TemplateLexer(text).tokens();
            var rangeBindings = collectRangeBindings(tokens);
            validateSubstitutions(tokens, holder);
            highlightTemplateIdentifiers(tokens, holder, localDefinitions, rangeBindings);
            highlightUnusedDefinitions(text, tokens, holder);
        } catch (TemplateLexerException e) {
            // The editor lexer sees raw JSON text. For expressions like {{ 'a\\'' }} inside JSON strings,
            // a raw pass may report "Unterminated string literal" even though runtime parsing is valid.
            if (isEscapedApostropheFalsePositive(text, e)) {
                return;
            }
            var position = Math.min(Math.max(e.getPosition(), 0), Math.max(text.length() - 1, 0));
            holder.newAnnotation(HighlightSeverity.ERROR, e.getMessage())
                    .range(TextRange.from(position, 1))
                    .create();
        } catch (Exception t) {
            if (text.isEmpty()) {
                return;
            }
            holder.newAnnotation(HighlightSeverity.WARNING, "JJTemplate lexer is unavailable: " + t.getClass().getSimpleName())
                    .range(TextRange.from(0, 1))
                    .create();
        }
    }

    private record IndexedToken(int index, Token token) {
        private TokenType type() {
            return token.type;
        }
    }

    private record NormalizedLookup(String source, int[] boundaries) {
    }

    private static void highlightUnusedDefinitions(String source,
                                                   List<Token> tokens,
                                                   AnnotationHolder holder) {
        for (var definition : UnusedDefinitionAnalyzer.findUnused(source, tokens)) {
            holder.newAnnotation(
                            HighlightSeverity.WEAK_WARNING,
                            "Definition '" + definition.name() + "' is never used"
                    )
                    .range(TextRange.create(definition.start(), definition.end()))
                    .textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
                    .create();
        }
    }

    private static boolean isEscapedApostropheFalsePositive(String text, TemplateLexerException error) {
        var message = error.getMessage();
        if (message == null
                || !message.contains("Unterminated string literal")
                        && !message.contains("Unexpected character '\\'")) {
            return false;
        }
        if (!text.contains("\\\\'")) {
            return false;
        }
        try {
            new TemplateLexer(text.replace("\\\\'", "\\'")).tokens();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void highlightTemplateIdentifiers(List<Token> tokens,
                                                     AnnotationHolder holder,
                                                     List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
                                                     Set<String> rangeBindings) {
        highlightTemplateIdentifiers(tokens, 0, holder, localDefinitions, rangeBindings, true);
    }

    private static void highlightTemplateIdentifiers(List<Token> tokens,
                                                     int baseOffset,
                                                     AnnotationHolder holder,
                                                     List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
                                                     Set<String> rangeBindings,
                                                     boolean markGlobalVariables) {
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.IDENT) {
                continue;
            }
            var key = identifierTextAttributes(
                    tokens,
                    i,
                    localDefinitions,
                    rangeBindings,
                    baseOffset + token.start
            );
            annotateRange(holder, baseOffset + token.start, baseOffset + token.end, key);
            if (markGlobalVariables && key == JJTemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE) {
                annotateGlobalVariableUsage(holder, token, baseOffset);
            }
        }
    }

    private static void annotateGlobalVariableUsage(AnnotationHolder holder, Token token, int baseOffset) {
        holder.newAnnotation(HighlightSeverity.INFORMATION, "Global variable '" + token.lexeme + "'")
                .range(TextRange.create(baseOffset + token.start, baseOffset + token.end))
                .enforcedTextAttributes(GLOBAL_VARIABLE_STRIPE_ATTRIBUTES)
                .create();
    }

    private static TextAttributes createGlobalVariableStripeAttributes() {
        var attributes = new TextAttributes();
        attributes.setErrorStripeColor(new JBColor(0x3574F0, 0x548AF7));
        return attributes;
    }

    static TextAttributesKey identifierTextAttributes(
            List<Token> tokens,
            int identifierIndex,
            List<UnusedDefinitionAnalyzer.UnusedDefinition> localDefinitions,
            Set<String> rangeBindings,
            int absoluteOffset
    ) {
        var token = tokens.get(identifierIndex);
        var localDefinition = localDefinitions.stream()
                .anyMatch(definition -> definition.name().equals(token.lexeme)
                        && definition.start() < absoluteOffset);
        return identifierTextAttributes(tokens, identifierIndex, localDefinition, rangeBindings);
    }

    static TextAttributesKey identifierTextAttributes(List<Token> tokens,
                                                      int identifierIndex,
                                                      Set<String> localDefinitions,
                                                      Set<String> rangeBindings) {
        return identifierTextAttributes(
                tokens,
                identifierIndex,
                localDefinitions.contains(tokens.get(identifierIndex).lexeme),
                rangeBindings
        );
    }

    private static TextAttributesKey identifierTextAttributes(
            List<Token> tokens,
            int identifierIndex,
            boolean localDefinition,
            Set<String> rangeBindings
    ) {
        if (isDefinitionName(tokens, identifierIndex) || isRangeBindingName(tokens, identifierIndex)) {
            return JJTemplateSyntaxHighlighter.OBJECT_KEY;
        }
        if (isFunctionCall(tokens, identifierIndex)) {
            return JJTemplateSyntaxHighlighter.TEMPLATE_FUNCTION;
        }
        if (isContextVariable(tokens, identifierIndex, localDefinition, rangeBindings)) {
            return JJTemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE;
        }
        return JJTemplateSyntaxHighlighter.TEMPLATE_VARIABLE;
    }
}
