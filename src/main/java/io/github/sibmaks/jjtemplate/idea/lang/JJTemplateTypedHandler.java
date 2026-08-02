package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class JJTemplateTypedHandler extends TypedHandlerDelegate {
    private static boolean isEscaped(@NotNull CharSequence chars, int quoteOffset) {
        var backslashes = 0;
        for (var i = quoteOffset - 1; i >= 0 && chars.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return (backslashes & 1) == 1;
    }

    @Override
    public @NotNull Result beforeCharTyped(char c,
                                           @NotNull Project project,
                                           @NotNull Editor editor,
                                           @NotNull PsiFile file,
                                           @NotNull FileType fileType) {
        if (!file.getLanguage().isKindOf(JJTemplateLanguage.INSTANCE)) {
            return Result.CONTINUE;
        }
        if (editor.getSelectionModel().hasSelection()) {
            return Result.CONTINUE;
        }

        if (c == '\'' && !isInSubstitution(editor)) {
            return Result.CONTINUE;
        }

        var close = switch (c) {
            case '"' -> '"';
            case '\'' -> '\'';
            case '{' -> '}';
            case '[' -> ']';
            default -> null;
        };
        if (close == null) {
            return Result.CONTINUE;
        }

        var document = editor.getDocument();
        var offset = editor.getCaretModel().getOffset();
        var chars = document.getCharsSequence();
        if (c == '"' && offset < chars.length() && chars.charAt(offset) == '"' && !isEscaped(chars, offset)) {
            editor.getCaretModel().moveToOffset(offset + 1);
            return Result.STOP;
        }
        if (c == '\'' && offset < chars.length() && chars.charAt(offset) == '\'' && !isEscaped(chars, offset)) {
            editor.getCaretModel().moveToOffset(offset + 1);
            return Result.STOP;
        }

        if (c == '"' && isEscaped(chars, offset)) {
            return Result.CONTINUE;
        }
        if (c == '\'' && isEscaped(chars, offset)) {
            // Inside JSON text, apostrophe escape in template strings must stay as \\'
            // (not \') to keep JSON valid while preserving template semantics.
            document.insertString(offset, "\\'");
            editor.getCaretModel().moveToOffset(offset + 2);
            return Result.STOP;
        }

        if (c == '{' && offset > 0 && chars.charAt(offset - 1) == '{') {
            return Result.CONTINUE;
        }

        document.insertString(offset, String.valueOf(c) + close);
        editor.getCaretModel().moveToOffset(offset + 1);
        return Result.STOP;
    }

    @Override
    public @NotNull Result checkAutoPopup(char charTyped,
                                          @NotNull Project project,
                                          @NotNull Editor editor,
                                          @NotNull PsiFile file) {
        if (!file.getLanguage().isKindOf(JJTemplateLanguage.INSTANCE)) {
            return Result.CONTINUE;
        }
        if ((charTyped == '.' || charTyped == ':') && isInSubstitution(editor)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            return Result.STOP;
        }
        return Result.CONTINUE;
    }

    private static boolean isInSubstitution(@NotNull Editor editor) {
        var lexer = new JJTemplateSyntaxLexer();
        var chars = editor.getDocument().getCharsSequence();
        lexer.start(chars, 0, chars.length(), 0);

        var offset = editor.getCaretModel().getOffset();
        var depth = 0;
        while (lexer.getTokenType() != null) {
            var tokenType = lexer.getTokenType();
            if (lexer.getTokenStart() >= offset) {
                break;
            }
            if (isTemplateOpen(tokenType)) {
                depth++;
            } else if (tokenType == JJTemplateTokenTypes.CLOSE && depth > 0) {
                depth--;
            }
            lexer.advance();
        }
        return depth > 0;
    }

    private static boolean isTemplateOpen(IElementType tokenType) {
        return tokenType == JJTemplateTokenTypes.OPEN_EXPR
                || tokenType == JJTemplateTokenTypes.OPEN_COND
                || tokenType == JJTemplateTokenTypes.OPEN_SPREAD;
    }
}
