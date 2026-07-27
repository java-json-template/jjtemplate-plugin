package io.github.sibmaks.jjtemplate.idea.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class UnusedDefinitionAnalyzer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UnusedDefinitionAnalyzer() {
    }

    static List<UnusedDefinition> findUnused(String source, List<Token> tokens) {
        if (!isValidJson(source)) {
            return List.of();
        }
        var parser = new JsonStructureParser(source);
        var metadata = parser.parse();
        var usedNames = collectUsedNames(tokens, metadata.rangeScopes());
        return metadata.definitions().stream()
                .filter(definition -> !usedNames.contains(definition.name()))
                .toList();
    }

    private static boolean isValidJson(String source) {
        try {
            MAPPER.readTree(source);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Set<String> collectUsedNames(List<Token> tokens, List<RangeScope> rangeScopes) {
        var usedNames = new HashSet<String>();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.IDENT || !isRootVariable(tokens, i)) {
                continue;
            }
            if (!isRangeBindingUsage(token, rangeScopes)) {
                usedNames.add(token.lexeme);
            }
        }
        return usedNames;
    }

    private static boolean isRootVariable(List<Token> tokens, int identIndex) {
        var dotIndex = previousNonTextTokenIndex(tokens, identIndex - 1);
        if (dotIndex < 0 || tokens.get(dotIndex).type != TokenType.DOT) {
            return false;
        }
        var beforeDotIndex = previousNonTextTokenIndex(tokens, dotIndex - 1);
        return beforeDotIndex < 0 || tokens.get(beforeDotIndex).type != TokenType.IDENT;
    }

    private static int previousNonTextTokenIndex(List<Token> tokens, int from) {
        for (int i = from; i >= 0; i--) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isRangeBindingUsage(Token token, List<RangeScope> rangeScopes) {
        for (var rangeScope : rangeScopes) {
            if (token.start < rangeScope.start() || token.end > rangeScope.end()) {
                continue;
            }
            if (rangeScope.bindings().contains(token.lexeme)) {
                return true;
            }
        }
        return false;
    }

    record UnusedDefinition(String name, int start, int end) {
    }

    private record AnalysisMetadata(List<UnusedDefinition> definitions, List<RangeScope> rangeScopes) {
    }

    private record RangeScope(int start, int end, Set<String> bindings) {
    }

    private record JsonString(String value, int contentStart, int contentEnd) {
    }

    private record DefinitionKey(String name, int start, int end, Set<String> rangeBindings) {
    }

    private static final class JsonStructureParser {
        private final String source;
        private final List<UnusedDefinition> definitions = new ArrayList<>();
        private final List<RangeScope> rangeScopes = new ArrayList<>();
        private int offset;

        private JsonStructureParser(String source) {
            this.source = source;
        }

        private AnalysisMetadata parse() {
            skipWhitespace();
            parseValue(false, true);
            return new AnalysisMetadata(List.copyOf(definitions), List.copyOf(rangeScopes));
        }

        private void parseValue(boolean definitionObject, boolean rootValue) {
            skipWhitespace();
            if (offset >= source.length()) {
                return;
            }
            switch (source.charAt(offset)) {
                case '{' -> parseObject(definitionObject, rootValue);
                case '[' -> parseArray(false);
                case '"' -> parseString();
                default -> skipScalar();
            }
        }

        private void parseObject(boolean definitionObject, boolean rootObject) {
            offset++;
            skipWhitespace();
            while (offset < source.length() && source.charAt(offset) != '}') {
                var key = parseString();
                skipWhitespace();
                offset++;
                skipWhitespace();
                var valueStart = offset;
                var definitionsArray = rootObject && "definitions".equals(key.value());
                if (definitionsArray && offset < source.length() && source.charAt(offset) == '[') {
                    parseArray(true);
                } else {
                    parseValue(false, false);
                }
                var valueEnd = offset;
                var definitionKey = parseDefinitionKey(key);
                if (definitionObject && definitionKey != null) {
                    definitions.add(new UnusedDefinition(
                            definitionKey.name(),
                            definitionKey.start(),
                            definitionKey.end()
                    ));
                }
                if (definitionKey != null && !definitionKey.rangeBindings().isEmpty()) {
                    rangeScopes.add(new RangeScope(valueStart, valueEnd, definitionKey.rangeBindings()));
                }
                skipWhitespace();
                if (offset < source.length() && source.charAt(offset) == ',') {
                    offset++;
                    skipWhitespace();
                }
            }
            if (offset < source.length()) {
                offset++;
            }
        }

        private void parseArray(boolean definitionsArray) {
            offset++;
            skipWhitespace();
            while (offset < source.length() && source.charAt(offset) != ']') {
                var definitionObject = definitionsArray && source.charAt(offset) == '{';
                parseValue(definitionObject, false);
                skipWhitespace();
                if (offset < source.length() && source.charAt(offset) == ',') {
                    offset++;
                    skipWhitespace();
                }
            }
            if (offset < source.length()) {
                offset++;
            }
        }

        private JsonString parseString() {
            var contentStart = ++offset;
            var escaped = false;
            while (offset < source.length()) {
                var character = source.charAt(offset);
                if (escaped) {
                    escaped = false;
                    offset++;
                    continue;
                }
                if (character == '\\') {
                    escaped = true;
                    offset++;
                    continue;
                }
                if (character == '"') {
                    var contentEnd = offset++;
                    return new JsonString(source.substring(contentStart, contentEnd), contentStart, contentEnd);
                }
                offset++;
            }
            return new JsonString(source.substring(contentStart), contentStart, source.length());
        }

        private void skipScalar() {
            while (offset < source.length()) {
                var character = source.charAt(offset);
                if (character == ',' || character == '}' || character == ']') {
                    return;
                }
                offset++;
            }
        }

        private void skipWhitespace() {
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
        }

        private static DefinitionKey parseDefinitionKey(JsonString key) {
            if (isPlainIdentifier(key.value())) {
                return new DefinitionKey(key.value(), key.contentStart(), key.contentEnd(), Set.of());
            }
            try {
                var tokens = new TemplateLexer(key.value()).tokens();
                for (int i = 0; i < tokens.size(); i++) {
                    var token = tokens.get(i);
                    if (token.type != TokenType.IDENT) {
                        continue;
                    }
                    var nextIndex = nextNonTextTokenIndex(tokens, i + 1);
                    if (nextIndex < 0 || tokens.get(nextIndex).type != TokenType.KEYWORD) {
                        continue;
                    }
                    var keyword = tokens.get(nextIndex);
                    if (Keyword.SWITCH.eq(keyword.lexeme)) {
                        return new DefinitionKey(
                                token.lexeme,
                                key.contentStart() + token.start,
                                key.contentStart() + token.end,
                                Set.of()
                        );
                    }
                    if (Keyword.RANGE.eq(keyword.lexeme)) {
                        return new DefinitionKey(
                                token.lexeme,
                                key.contentStart() + token.start,
                                key.contentStart() + token.end,
                                collectRangeBindings(tokens, nextIndex)
                        );
                    }
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }

        private static boolean isPlainIdentifier(String value) {
            try {
                var tokens = new TemplateLexer("{{ " + value + " }}").tokens();
                var identifierCount = 0;
                for (var token : tokens) {
                    if (token.type == TokenType.TEXT
                            || token.type == TokenType.OPEN_EXPR
                            || token.type == TokenType.CLOSE) {
                        continue;
                    }
                    if (token.type != TokenType.IDENT || !value.equals(token.lexeme)) {
                        return false;
                    }
                    identifierCount++;
                }
                return identifierCount == 1;
            } catch (Exception ignored) {
                return false;
            }
        }

        private static Set<String> collectRangeBindings(List<Token> tokens, int rangeKeywordIndex) {
            var itemIndex = nextNonTextTokenIndex(tokens, rangeKeywordIndex + 1);
            if (itemIndex < 0 || tokens.get(itemIndex).type != TokenType.IDENT) {
                return Set.of();
            }
            var commaIndex = nextNonTextTokenIndex(tokens, itemIndex + 1);
            if (commaIndex < 0 || tokens.get(commaIndex).type != TokenType.COMMA) {
                return Set.of(tokens.get(itemIndex).lexeme);
            }
            var indexIndex = nextNonTextTokenIndex(tokens, commaIndex + 1);
            if (indexIndex < 0 || tokens.get(indexIndex).type != TokenType.IDENT) {
                return Set.of(tokens.get(itemIndex).lexeme);
            }
            return Set.of(tokens.get(itemIndex).lexeme, tokens.get(indexIndex).lexeme);
        }

        private static int nextNonTextTokenIndex(List<Token> tokens, int from) {
            for (int i = from; i < tokens.size(); i++) {
                if (tokens.get(i).type != TokenType.TEXT) {
                    return i;
                }
            }
            return -1;
        }
    }
}
