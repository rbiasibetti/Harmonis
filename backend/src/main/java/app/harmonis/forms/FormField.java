package app.harmonis.forms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Definicao de um campo do formulario (porta estendida de FormSchema::newField). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormField(
        String id,
        String key,
        String type,
        String label,
        String placeholder,
        String help,
        boolean required,
        List<String> options,
        String display,            // single: combo|radio ; multiselect: combo|checkbox
        String width,              // full|half|third (layout em grid responsivo)
        Validation validation,     // regras extras (opcional)
        FieldCondition visibleWhen // libera/exibe o campo somente quando a condicao passa (opcional)
) {
    public List<String> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }

    /** Regras de validacao adicionais de um campo. Todos os campos sao opcionais (null = nao aplica). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(
            String pattern,        // regex (text/textarea/email)
            String patternMessage, // mensagem custom quando a regex falha
            Double min,            // valor minimo (number) / tambem usado como limite
            Double max,            // valor maximo (number)
            Boolean positive,      // number > 0
            Boolean integer,       // number inteiro
            Integer minLength,     // tamanho minimo (text)
            Integer maxLength,     // tamanho maximo (text)
            String minDate,        // data minima ISO (date)
            String maxDate,        // data maxima ISO (date)
            String afterField,     // este campo deve ser > o campo referenciado (date/number)
            String beforeField     // este campo deve ser < o campo referenciado (date/number)
    ) {}

    /** Condicao campo/operador/valor (mesmos operadores do motor de workflow). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldCondition(String field, String operator, String value) {}
}
