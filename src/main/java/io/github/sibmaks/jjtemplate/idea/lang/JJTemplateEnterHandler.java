package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class JJTemplateEnterHandler extends EnterHandlerDelegateAdapter {

    @Override
    public @NotNull EnterHandlerDelegate.Result preprocessEnter(
            @NotNull PsiFile file,
            @NotNull Editor editor,
            @NotNull Ref<Integer> caretOffset,
            @NotNull Ref<Integer> caretAdvance,
            @NotNull DataContext dataContext,
            @NotNull EditorActionHandler originalHandler
    ) {
        if (!file.getLanguage().isKindOf(JJTemplateLanguage.INSTANCE)
                || editor.getSelectionModel().hasSelection()) {
            return EnterHandlerDelegate.Result.Continue;
        }

        var offset = caretOffset.get();
        var document = editor.getDocument();
        var chars = document.getCharsSequence();
        if (offset <= 0 || offset >= chars.length()
                || !isEmptyContainer(chars.charAt(offset - 1), chars.charAt(offset))) {
            return EnterHandlerDelegate.Result.Continue;
        }

        var lineNumber = document.getLineNumber(offset);
        var lineStart = document.getLineStartOffset(lineNumber);
        var contentStart = lineStart;
        while (contentStart < offset && Character.isWhitespace(chars.charAt(contentStart))) {
            contentStart++;
        }
        var parentIndent = chars.subSequence(lineStart, contentStart).toString();
        var indentSize = Math.max(1, CodeStyle.getIndentSize(file));
        var childIndent = parentIndent + " ".repeat(indentSize);
        var insertion = "\n" + childIndent + "\n" + parentIndent;

        document.insertString(offset, insertion);
        editor.getCaretModel().moveToOffset(offset + 1 + childIndent.length());
        return EnterHandlerDelegate.Result.Stop;
    }

    private static boolean isEmptyContainer(char open, char close) {
        return open == '{' && close == '}' || open == '[' && close == ']';
    }
}
