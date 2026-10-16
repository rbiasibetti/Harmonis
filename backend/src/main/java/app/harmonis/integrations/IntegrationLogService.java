package app.harmonis.integrations;

import app.harmonis.integrations.IntegrationDtos.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Consulta dos logs de integracao (listagem filtrada, detalhe e contadores). */
@Service
public class IntegrationLogService {

    private final IntegrationLogRepository logs;

    public IntegrationLogService(IntegrationLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public LogPage search(String status, String event, Long integrationId, Long formId,
                          Instant from, Instant to, String q, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        Specification<IntegrationLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (notBlank(status)) p.add(cb.equal(root.get("status"), status));
            if (notBlank(event)) p.add(cb.equal(root.get("event"), event));
            if (integrationId != null) p.add(cb.equal(root.get("integrationId"), integrationId));
            if (formId != null) p.add(cb.equal(root.get("formId"), formId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
            if (notBlank(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("integrationName")), like),
                        cb.like(cb.lower(root.get("formName")), like),
                        cb.like(cb.lower(root.get("errorMessage")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        Page<IntegrationLog> result = logs.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new LogPage(result.getContent().stream().map(IntegrationLogService::toRow).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public LogDetail detail(long id) {
        return toDetail(logs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de log não encontrado.")));
    }

    @Transactional(readOnly = true)
    public LogStats stats() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return new LogStats(
                logs.countByStatusAndCreatedAtGreaterThanEqual(IntegrationLog.SUCCESS, since),
                logs.countByStatusAndCreatedAtGreaterThanEqual(IntegrationLog.ERROR, since),
                logs.countByStatusAndCreatedAtGreaterThanEqual(IntegrationLog.SKIPPED, since));
    }

    static LogRow toRow(IntegrationLog l) {
        return new LogRow(l.getId(), l.getDeliveryId(), l.getAttempt(), l.getIntegrationId(), l.getIntegrationName(),
                l.getFormId(), l.getFormName(), l.getSubmissionId(), l.getEvent(), l.getStatus(),
                l.getHttpStatus(), l.getDurationMs(), l.getErrorMessage(), l.isWillRetry(),
                l.getTriggeredBy() != null, l.getCreatedAt());
    }

    static LogDetail toDetail(IntegrationLog l) {
        return new LogDetail(l.getId(), l.getDeliveryId(), l.getAttempt(), l.getIntegrationId(), l.getIntegrationName(),
                l.getFormId(), l.getFormName(), l.getSubmissionId(), l.getEvent(), l.getStatus(),
                l.getHttpStatus(), l.getDurationMs(), l.getErrorMessage(), l.isWillRetry(),
                l.getTriggeredBy() != null, l.getCreatedAt(),
                l.getMethod(), l.getRequestUrl(), l.getRequestBody(), l.getResponseBody());
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
