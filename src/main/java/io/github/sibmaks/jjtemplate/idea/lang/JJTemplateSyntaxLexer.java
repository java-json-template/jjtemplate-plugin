package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JJTemplateSyntaxLexer extends LexerBase {
    private CharSequence buffer = "";
    private int startOffset;
    private int endOffset;
    private List<Lexeme> tokens = Collections.emptyList();
    private int index;

    private static IElementType mapType(TokenType tokenType) {
        return switch (tokenType) {
            case TEXT -> JJTemplateTokenTypes.TEXT;
            case OPEN_EXPR -> JJTemplateTokenTypes.OPEN_EXPR;
            case OPEN_COND -> JJTemplateTokenTypes.OPEN_COND;
            case OPEN_SPREAD -> JJTemplateTokenTypes.OPEN_SPREAD;
            case CLOSE -> JJTemplateTokenTypes.CLOSE;
            case PIPE -> JJTemplateTokenTypes.PIPE;
            case DOT -> JJTemplateTokenTypes.DOT;
            case SAFE_DOT -> JJTemplateTokenTypes.SAFE_DOT;
            case COMMA -> JJTemplateTokenTypes.COMMA;
            case COLON -> JJTemplateTokenTypes.COLON;
            case QUESTION -> JJTemplateTokenTypes.QUESTION;
            case LPAREN -> JJTemplateTokenTypes.LPAREN;
            case RPAREN -> JJTemplateTokenTypes.RPAREN;
            case STRING -> JJTemplateTokenTypes.STRING;
            case NUMBER -> JJTemplateTokenTypes.NUMBER;
            case BOOLEAN -> JJTemplateTokenTypes.BOOLEAN;
            case NULL -> JJTemplateTokenTypes.NULL;
            case IDENT -> JJTemplateTokenTypes.IDENT;
            case KEYWORD -> JJTemplateTokenTypes.KEYWORD;
        };
    }

    private static List<Lexeme> toLexemes(List<Token> sourceTokens, String source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<Lexeme>(sourceTokens.size());
        for (var token : sourceTokens) {
            if (token.type == TokenType.TEXT) {
                appendPlainTextTokens(source, token.start, token.end, result);
                continue;
            }
            if (token.type == TokenType.STRING) {
                appendStringToken(source, token.start, token.end, result);
                continue;
            }
            result.add(new Lexeme(mapType(token.type), token.start, token.end));
        }
        result = sanitizeLexemes(result, source);
        if (result.isEmpty()) {
            return fallbackTokens(source);
        }
        return result;
    }

    private static ArrayList<Lexeme> sanitizeLexemes(List<Lexeme> sourceLexemes, String source) {
        var sourceLength = source.length();
        var sanitized = new ArrayList<Lexeme>(sourceLexemes.size());
        var cursor = 0;
        for (var lexeme : sourceLexemes) {
            var start = Math.max(0, Math.min(lexeme.start(), sourceLength));
            var end = Math.max(0, Math.min(lexeme.end(), sourceLength));
            if (start < cursor) {
                start = cursor;
            }
            if (start > cursor) {
                appendPlainTextTokens(source, cursor, start, sanitized);
                cursor = start;
            }
            if (end <= start) {
                continue;
            }
            sanitized.add(new Lexeme(lexeme.type(), start, end));
            cursor = end;
            if (cursor >= sourceLength) {
                break;
            }
        }
        if (cursor < sourceLength) {
            appendPlainTextTokens(source, cursor, sourceLength, sanitized);
        }
        return sanitized;
    }

    private static void appendStringToken(String source, int from, int to, List<Lexeme> out) {
        if (to - from < 2) {
            out.add(new Lexeme(JJTemplateTokenTypes.STRING, from, to));
            return;
        }
        var contentStart = from + 1;
        var contentEnd = to - 1;
        var cursor = from;
        var index = contentStart;

        while (index < contentEnd) {
            if (!TemplateTextScanner.isTemplateStart(source, index)) {
                index++;
                continue;
            }
            var templateEnd = TemplateTextScanner.findTemplateEnd(source, index, contentEnd);
            if (templateEnd < 0) {
                break;
            }
            if (cursor < index) {
                out.add(new Lexeme(JJTemplateTokenTypes.STRING, cursor, index));
            }
            appendTemplateExpressionTokens(source, index, templateEnd, out);
            cursor = templateEnd;
            index = templateEnd;
        }

        if (cursor < to) {
            out.add(new Lexeme(JJTemplateTokenTypes.STRING, cursor, to));
        }
    }

    private static void appendTemplateExpressionTokens(String source, int from, int to, List<Lexeme> out) {
        var templateSource = source.substring(from, to);
        try {
            var tokens = new TemplateLexer(templateSource).tokens();
            for (var token : tokens) {
                var tokenStart = from + token.start;
                var tokenEnd = from + token.end;
                if (token.type == TokenType.STRING) {
                    appendStringToken(source, tokenStart, tokenEnd, out);
                    continue;
                }
                var type = token.type == TokenType.TEXT ? JJTemplateTokenTypes.STRING : mapType(token.type);
                out.add(new Lexeme(type, tokenStart, tokenEnd));
            }
        } catch (Exception ignored) {
            var openType = switch (source.charAt(from + 1)) {
                case '?' -> JJTemplateTokenTypes.OPEN_COND;
                case '.' -> JJTemplateTokenTypes.OPEN_SPREAD;
                default -> JJTemplateTokenTypes.OPEN_EXPR;
            };
            out.add(new Lexeme(openType, from, Math.min(from + 2, to)));
            if (to - from > 4) {
                out.add(new Lexeme(JJTemplateTokenTypes.STRING, from + 2, to - 2));
            }
            if (to - from >= 2) {
                out.add(new Lexeme(JJTemplateTokenTypes.CLOSE, to - 2, to));
            }
        }
    }

    private static List<Token> normalizeTokens(List<Token> sourceTokens, String source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        var length = source.length();
        var normalized = new ArrayList<Token>(sourceTokens.size());
        var currentStart = 0;
        for (var token : sourceTokens) {
            var start = Math.max(token.start, 0);
            var end = Math.min(token.end, length);
            if (start > currentStart) {
                normalized.add(new Token(TokenType.TEXT, source.substring(currentStart, start), currentStart, start));
                currentStart = start;
            }
            if (start < currentStart) {
                start = currentStart;
            }
            if (end <= start) {
                continue;
            }
            normalized.add(new Token(token.type, source.substring(start, end), start, end));
            currentStart = end;
            if (currentStart >= length) {
                break;
            }
        }
        if (currentStart < length) {
            normalized.add(new Token(TokenType.TEXT, source.substring(currentStart), currentStart, length));
        }
        if (normalized.isEmpty()) {
            return List.of(new Token(TokenType.TEXT, source, 0, source.length()));
        }
        return normalized;
    }

    private static List<Lexeme> fallbackTokens(String source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<Lexeme>();
        appendPlainTextTokens(source, 0, source.length(), result);
        if (result.isEmpty()) {
            result.add(new Lexeme(JJTemplateTokenTypes.TEXT, 0, source.length()));
        }
        return result;
    }

    private static void appendPlainTextTokens(String source, int from, int to, List<Lexeme> out) {
        var cursor = from;
        while (cursor < to) {
            if (cursor + 1 < to) {
                var templateType = getTemplateType(source, cursor);
                if (templateType != null) {
                    out.add(new Lexeme(templateType, cursor, cursor + 2));
                    cursor += 2;
                    continue;
                }
            }

            var ch = source.charAt(cursor);
            var punctType = mapPlainDelimiter(ch);
            if (punctType != null) {
                out.add(new Lexeme(punctType, cursor, cursor + 1));
                cursor++;
                continue;
            }

            var start = cursor;
            while (cursor < to && mapPlainDelimiter(source.charAt(cursor)) == null) {
                cursor++;
            }
            out.add(new Lexeme(JJTemplateTokenTypes.TEXT, start, cursor));
        }
    }

    private static @Nullable IElementType getTemplateType(String source, int cursor) {
        var first = source.charAt(cursor);
        var second = source.charAt(cursor + 1);
        return switch (first) {
            case '{' -> switch (second) {
                case '{' -> JJTemplateTokenTypes.OPEN_EXPR;
                case '?' -> JJTemplateTokenTypes.OPEN_COND;
                case '.' -> JJTemplateTokenTypes.OPEN_SPREAD;
                default -> null;
            };
            case '}' -> second == '}' ? JJTemplateTokenTypes.CLOSE : null;
            default -> null;
        };
    }

    private static IElementType mapPlainDelimiter(char ch) {
        return switch (ch) {
            case '{' -> JJTemplateTokenTypes.LBRACE;
            case '}' -> JJTemplateTokenTypes.RBRACE;
            case '[' -> JJTemplateTokenTypes.LBRACKET;
            case ']' -> JJTemplateTokenTypes.RBRACKET;
            case '(' -> JJTemplateTokenTypes.LPAREN;
            case ')' -> JJTemplateTokenTypes.RPAREN;
            default -> null;
        };
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.index = 0;
        var source = buffer.subSequence(startOffset, endOffset).toString();
        try {
            this.tokens = toLexemes(normalizeTokens(new TemplateLexer(source).tokens(), source), source);
        } catch (Exception e) {
            this.tokens = fallbackTokens(source);
        }
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        if (index >= tokens.size()) {
            return null;
        }
        return tokens.get(index).type;
    }

    @Override
    public int getTokenStart() {
        if (index >= tokens.size()) {
            return endOffset;
        }
        return startOffset + tokens.get(index).start();
    }

    @Override
    public int getTokenEnd() {
        if (index >= tokens.size()) {
            return endOffset;
        }
        var token = tokens.get(index);
        var tokenStart = startOffset + token.start();
        var tokenEnd = startOffset + token.end();
        if (tokenEnd <= tokenStart) {
            return Math.min(endOffset, tokenStart + 1);
        }
        return Math.min(endOffset, tokenEnd);
    }

    @Override
    public void advance() {
        if (index < tokens.size()) {
            index++;
        }
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private record Lexeme(IElementType type, int start, int end) {
    }
}
