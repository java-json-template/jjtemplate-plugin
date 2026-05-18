package io.github.sibmaks.jjtemplate.idea.toolwindow;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateFileType;

public final class JjtemplateIndentOptions {

    private JjtemplateIndentOptions() {
    }

    public static int getIndent(Project project) {
        var options = CodeStyle.getSettings(project).getIndentOptions(JjtemplateFileType.INSTANCE);
        return normalize(options.INDENT_SIZE);
    }

    public static void setIndent(Project project, int indent) {
        var options = CodeStyle.getSettings(project).getIndentOptions(JjtemplateFileType.INSTANCE);
        var normalized = normalize(indent);
        options.INDENT_SIZE = normalized;
        options.CONTINUATION_INDENT_SIZE = normalized;
        options.TAB_SIZE = normalized;
        options.USE_TAB_CHARACTER = false;
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
    }

    public static int normalize(int indent) {
        return Math.clamp(indent, 1, 8);
    }
}
