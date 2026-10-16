package app.harmonis.integrations;

import app.harmonis.forms.Form;
import app.harmonis.forms.FormRepository;
import app.harmonis.integrations.IntegrationDtos.HookRef;
import app.harmonis.integrations.IntegrationDtos.UsageRef;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Vinculos entre formularios, eventos do processo e integracoes publicadas. */
@Service
public class HookBindingService {

    private final FormHookRepository hooks;
    private final IntegrationRepository integrations;
    private final FormRepository forms;

    public HookBindingService(FormHookRepository hooks, IntegrationRepository integrations, FormRepository forms) {
        this.hooks = hooks;
        this.integrations = integrations;
        this.forms = forms;
    }

    /** Vinculos do formulario por evento (todas as chaves de BINDABLE presentes). */
    @Transactional(readOnly = true)
    public Map<String, List<HookRef>> refsFor(long formId) {
        Map<String, List<HookRef>> out = new LinkedHashMap<>();
        WebhookEvents.BINDABLE.forEach(e -> out.put(e, new ArrayList<>()));
        List<FormHook> rows = hooks.findByFormIdOrderByEventAscSortOrderAsc(formId);
        Map<Long, Integration> byId = integrations.findAllById(
                rows.stream().map(FormHook::getIntegrationId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Integration::getId, i -> i));
        for (FormHook h : rows) {
            Integration i = byId.get(h.getIntegrationId());
            if (i != null && out.containsKey(h.getEvent())) {
                out.get(h.getEvent()).add(new HookRef(i.getId(), i.getName(), i.isPublished()));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Long> integrationIdsFor(long formId, String event) {
        return hooks.findByFormIdAndEventOrderBySortOrderAsc(formId, event).stream()
                .map(FormHook::getIntegrationId).toList();
    }

    /**
     * Substitui TODOS os vinculos do formulario. Uma integracao nova precisa estar publicada;
     * vinculos ja existentes de integracoes despublicadas podem ser mantidos.
     */
    @Transactional
    public void replace(long formId, Map<String, List<Long>> wanted) {
        Map<String, Set<Long>> current = new HashMap<>();
        for (FormHook h : hooks.findByFormIdOrderByEventAscSortOrderAsc(formId)) {
            current.computeIfAbsent(h.getEvent(), k -> new HashSet<>()).add(h.getIntegrationId());
        }

        List<FormHook> toSave = new ArrayList<>();
        for (Map.Entry<String, List<Long>> e : wanted.entrySet()) {
            String event = e.getKey();
            if (!WebhookEvents.BINDABLE.contains(event)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento de integração inválido: " + event);
            }
            int order = 0;
            for (Long id : new LinkedHashSet<>(e.getValue() == null ? List.<Long>of() : e.getValue())) {
                Integration i = integrations.findById(id).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integração inexistente (id " + id + ")."));
                boolean already = current.getOrDefault(event, Set.of()).contains(id);
                if (!i.isPublished() && !already) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A integração \"" + i.getName() + "\" não está publicada.");
                }
                toSave.add(new FormHook(formId, event, id, order++));
            }
        }
        hooks.deleteByFormId(formId);
        hooks.flush(); // executa os deletes antes dos inserts (unique form+evento+integracao)
        hooks.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public List<UsageRef> usage(long integrationId) {
        List<FormHook> rows = hooks.findByIntegrationId(integrationId);
        Map<Long, String> names = forms.findAllById(rows.stream().map(FormHook::getFormId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Form::getId, Form::getName));
        return rows.stream()
                .map(h -> new UsageRef(h.getFormId(), names.getOrDefault(h.getFormId(), "(formulário removido)"), h.getEvent()))
                .sorted(Comparator.comparing(UsageRef::formName).thenComparing(UsageRef::event))
                .toList();
    }
}
