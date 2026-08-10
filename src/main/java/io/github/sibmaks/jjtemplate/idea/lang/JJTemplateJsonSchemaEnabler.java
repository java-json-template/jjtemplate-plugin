package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import org.jetbrains.annotations.NotNull;

/** Makes JJTemplate files available to IntelliJ's standard JSON Schema mappings and switcher. */
public final class JJTemplateJsonSchemaEnabler implements JsonSchemaEnabler {
    @Override
    public boolean isEnabledForFile(@NotNull VirtualFile file, @NotNull Project project) {
        return file.getFileType() == JJTemplateFileType.INSTANCE;
    }
}
