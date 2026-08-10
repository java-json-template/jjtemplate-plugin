package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.json.JsonLanguage;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonFile;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;

final class JJTemplateJsonSchemaValidationSupport {
    private JJTemplateJsonSchemaValidationSupport() {
    }

    static void validate(JJTemplateFile file, AnnotationHolder holder) {
        var virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        var schema = JsonSchemaService.Impl.get(file.getProject()).getSchemaObject(file);
        if (schema == null) {
            return;
        }

        var jsonPsi = PsiFileFactory.getInstance(file.getProject())
                .createFileFromText(file.getName() + ".json", JsonLanguage.INSTANCE, file.getText());
        if (!(jsonPsi instanceof JsonFile jsonFile)
                || PsiTreeUtil.findChildOfType(jsonFile, PsiErrorElement.class) != null) {
            return;
        }
        var root = jsonFile.getTopLevelValue();
        if (root == null) {
            return;
        }

        var walker = JsonOriginalPsiWalker.INSTANCE;
        var adapter = walker.createValueAdapter(root);
        if (adapter == null) {
            return;
        }
        var match = new JsonSchemaResolver(
                file.getProject(),
                schema,
                new JsonPointerPosition(),
                adapter
        ).detailedResolve();
        var checker = JsonSchemaAnnotatorChecker.checkByMatchResult(
                file.getProject(),
                adapter,
                match,
                new JsonComplianceCheckerOptions(false)
        );
        var sourceLength = file.getTextLength();
        checker.getErrors().forEach((element, error) -> {
            var range = fitRange(walker.adjustErrorHighlightingRange(element), sourceLength);
            if (range != null) {
                holder.newAnnotation(HighlightSeverity.WARNING, error.getMessage())
                        .range(range)
                        .create();
            }
        });
    }

    private static TextRange fitRange(TextRange range, int sourceLength) {
        if (sourceLength == 0 || range == null) {
            return null;
        }
        var start = Math.min(Math.max(range.getStartOffset(), 0), sourceLength - 1);
        var end = Math.min(Math.max(range.getEndOffset(), start + 1), sourceLength);
        return TextRange.create(start, end);
    }
}
