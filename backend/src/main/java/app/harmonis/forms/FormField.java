package app.harmonis.forms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Definicao de um campo do formulario (porta de FormSchema::newField). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormField(
        String id,
        String key,
        String type,
        String label,
        String placeholder,
        String help,
        boolean required,
        List<String> options
) {
    public List<String> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }
}
