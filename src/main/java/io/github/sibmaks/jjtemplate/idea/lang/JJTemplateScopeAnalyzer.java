package io.github.sibmaks.jjtemplate.idea.lang;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JJTemplateScopeAnalyzer {

    private JJTemplateScopeAnalyzer() {
    }

    public record Scope(List<BindingScope> ranges) {

        public @NotNull Set<String> bindingsAt(int offset) {
            var bindings = new LinkedHashSet<String>();
            for (var range : ranges) {
                if (range.contains(offset)) {
                    bindings.addAll(range.bindings());
                }
            }
            return bindings;
        }

        public boolean isRangeBindingAt(@NotNull String name, int offset) {
            for (var range : ranges) {
                if (range.contains(offset) && range.bindings().contains(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record BindingScope(int start, int end, Set<String> bindings) {

        private boolean contains(int offset) {
            return offset >= start && offset < end;
        }
    }

    public static @NotNull Scope analyze(@NotNull String source) {
        var ranges = UnusedDefinitionAnalyzer.findRangeScopes(source).stream()
                .map(range -> new BindingScope(range.start(), range.end(), range.bindings()))
                .toList();
        return new Scope(ranges);
    }
}
