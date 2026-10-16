package app.harmonis.forms;

import app.harmonis.identity.UserRepository;
import app.harmonis.notifications.NotificationRepository;
import app.harmonis.workflow.ApprovalService;
import app.harmonis.workflow.ProcessInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormServiceDeleteTest {

    private FormRepository forms;
    private FormAttachmentService attachments;
    private ProcessInstanceRepository instances;
    private NotificationRepository notifications;
    private FormService service;

    @BeforeEach
    void setUp() {
        forms = mock(FormRepository.class);
        attachments = mock(FormAttachmentService.class);
        instances = mock(ProcessInstanceRepository.class);
        notifications = mock(NotificationRepository.class);
        service = new FormService(forms, mock(FormSubmissionRepository.class), mock(UserRepository.class),
                mock(FormSchemaService.class), mock(ApprovalService.class), attachments,
                instances, notifications, new ObjectMapper());
    }

    @Test
    void deleteRemovesDependentsInOrderBeforeTheForm() {
        Form form = new Form();
        ReflectionTestUtils.setField(form, "id", 7L);
        when(forms.findById(7L)).thenReturn(Optional.of(form));

        service.delete(7L);

        InOrder order = inOrder(attachments, notifications, instances, forms);
        order.verify(attachments).deleteAllForForm(7L);
        order.verify(notifications).deleteByProcessFormId(7L);
        order.verify(instances).deleteByFormId(7L);
        order.verify(forms).delete(form);
    }

    @Test
    void deleteUnknownFormIs404AndTouchesNothing() {
        when(forms.findById(9L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(9L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(attachments, notifications, instances);
        verify(forms, never()).delete(any());
    }
}
