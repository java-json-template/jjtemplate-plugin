package io.github.sibmaks.jjtemplate.idea.statusbar;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;
import io.github.sibmaks.jjtemplate.idea.lang.JJTemplateFileType;
import io.github.sibmaks.jjtemplate.idea.toolwindow.JJTemplateIndentOptions;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.Optional;

public final class JJTemplateIndentStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {
    public static final String WIDGET_ID = "JJTemplateIndent";

    private final Project project;
    private Optional<StatusBar> statusBar = Optional.empty();

    public JJTemplateIndentStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return WIDGET_ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = Optional.of(statusBar);
        project.getMessageBus()
                .connect(this)
                .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new SelectedFileListener());
    }

    @Override
    public void dispose() {
        this.statusBar = Optional.empty();
    }

    @Override
    public @NotNull WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public @NotNull String getText() {
        if (!hasOpenJJTemplateFile()) {
            return "";
        }
        return "JJT Indent: " + JJTemplateIndentOptions.getIndent(project);
    }

    @Override
    public float getAlignment() {
        return Component.CENTER_ALIGNMENT;
    }

    @Override
    public @NotNull String getTooltipText() {
        if (!hasOpenJJTemplateFile()) {
            return "";
        }
        return "JJTemplate indent used by Reformat File";
    }

    @Override
    public @NotNull Consumer<MouseEvent> getClickConsumer() {
        return event -> chooseIndent();
    }

    private void chooseIndent() {
        if (!hasOpenJJTemplateFile()) {
            Messages.showInfoMessage(project, "Open a JJTemplate file (*.jjt, *.jjtemplate) first.", "JJTemplate");
            return;
        }

        var current = JJTemplateIndentOptions.getIndent(project);
        var values = new String[]{"1", "2", "3", "4", "5", "6", "7", "8"};
        var selected = Messages.showEditableChooseDialog(
                "Select indent size for JJTemplate files.",
                "JJTemplate Indent",
                null,
                values,
                String.valueOf(current),
                new IndentInputValidator(values)
        );
        if (selected == null) {
            return;
        }
        JJTemplateIndentOptions.setIndent(project, Integer.parseInt(selected));
        statusBar.ifPresent(it -> it.updateWidget(ID()));
    }

    private boolean hasOpenJJTemplateFile() {
        var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return false;
        }
        var file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        return isJJTemplateFile(file);
    }

    private static boolean isJJTemplateFile(VirtualFile file) {
        if (file == null) {
            return false;
        }
        if (file.getFileType() == JJTemplateFileType.INSTANCE) {
            return true;
        }
        var extension = file.getExtension();
        return "jjt".equalsIgnoreCase(extension) || "jjtemplate".equalsIgnoreCase(extension);
    }

    private final class SelectedFileListener implements FileEditorManagerListener {
        @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
            statusBar.ifPresent(it -> it.updateWidget(ID()));
        }
    }

    private record IndentInputValidator(String[] values) implements InputValidator {
        @Override
        public boolean checkInput(String inputString) {
            for (var value : values) {
                if (value.equals(inputString)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canClose(String inputString) {
            return checkInput(inputString);
        }
    }
}
