package com.javanauta.ts.taskscheduler.application.service;

import com.javanauta.ts.taskscheduler.application.data.NotificationResultDetails;
import com.javanauta.ts.taskscheduler.application.data.TaskData;
import com.javanauta.ts.taskscheduler.application.data.enums.NotificationResult;
import com.javanauta.ts.taskscheduler.application.exception.enums.ServiceExceptionCode;
import com.javanauta.ts.taskscheduler.domain.model.Task;
import com.javanauta.ts.taskscheduler.domain.model.enums.NotificationStatus;
import com.javanauta.ts.taskscheduler.ports.out.messaging.NotificationRequestPublisher;
import com.javanauta.ts.taskscheduler.ports.out.persistence.TaskPersister;
import com.javanauta.ts.taskscheduler.ports.out.security.PrincipalProvider;
import com.javanauta.ts.taskscheduler.shared.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock
    private TaskPersister taskPersister;

    @Mock
    private PrincipalProvider principalProvider;

    @Mock
    private NotificationRequestPublisher notificationRequestPublisher;

    @InjectMocks
    private TaskService underTest;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "user@example.com";
    private static final String TASK_ID = "task-id";

    private static final Instant SCHEDULED_DATE_TIME =
            Instant.now().plusSeconds(3600);

    private static TaskData validTaskData() {
        return new TaskData(
                "Test task",
                "Test description",
                SCHEDULED_DATE_TIME,
                ZoneId.of("Europe/Dublin"));
    }

    private static Task taskOwnedBy(UUID userId) {
        Task task = Task.create(validTaskData(), userId, USER_EMAIL);
        task.setId(TASK_ID);
        return task;
    }

    // createTask

    @Test
    void createTask_shouldCreateAndPersistTaskForCurrentUser() {
        TaskData taskData = validTaskData();
        Task savedTask = taskOwnedBy(USER_ID);

        when(principalProvider.getId()).thenReturn(USER_ID);
        when(principalProvider.getEmail()).thenReturn(USER_EMAIL);
        when(taskPersister.save(any(Task.class))).thenReturn(savedTask);

        Task result = underTest.createTask(taskData);

        assertThat(result).isSameAs(savedTask);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskPersister).save(taskCaptor.capture());

        Task taskToSave = taskCaptor.getValue();
        assertThat(taskToSave.getName()).isEqualTo(taskData.name());
        assertThat(taskToSave.getDescription()).isEqualTo(taskData.description());
        assertThat(taskToSave.getScheduledDateTime())
                .isEqualTo(taskData.scheduledDateTime());
        assertThat(taskToSave.getUserId()).isEqualTo(USER_ID);
        assertThat(taskToSave.getUserEmail()).isEqualTo(USER_EMAIL);
        assertThat(taskToSave.getNotificationStatus())
                .isEqualTo(NotificationStatus.PENDING);
    }

    // deleteTask

    @Test
    void deleteTask_shouldDeleteOwnedTask() {
        Task task = taskOwnedBy(USER_ID);

        when(principalProvider.getId()).thenReturn(USER_ID);
        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));

        underTest.deleteTask(TASK_ID);

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister).deleteById(TASK_ID);
    }

    @Test
    void deleteTask_shouldRejectWhenTaskDoesNotExist() {
        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underTest.deleteTask(TASK_ID))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(ServiceExceptionCode.TASK_NOT_FOUND);
                });

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).deleteById(any());
        verifyNoInteractions(principalProvider);
    }

    @Test
    void deleteTask_shouldRejectWhenTaskDoesNotBelongToCurrentUser() {
        Task task = taskOwnedBy(OTHER_USER_ID);

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(principalProvider.getId()).thenReturn(USER_ID);

        assertThatThrownBy(() -> underTest.deleteTask(TASK_ID))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(ServiceExceptionCode.NO_TASK_OWNERSHIP);
                });

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).deleteById(any());
    }

    // updateTask

    @Test
    void updateTask_shouldUpdateAndPersistOwnedTask() {
        Task task = taskOwnedBy(USER_ID);
        TaskData updateData = new TaskData(
                "Updated task",
                "Updated description",
                Instant.now().plusSeconds(7200),
                ZoneId.of("America/New_York"));

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(principalProvider.getId()).thenReturn(USER_ID);
        when(taskPersister.save(task)).thenReturn(task);

        Task result = underTest.updateTask(updateData, TASK_ID);

        assertThat(result).isSameAs(task);
        assertThat(task.getName()).isEqualTo("Updated task");
        assertThat(task.getDescription()).isEqualTo("Updated description");
        assertThat(task.getScheduledDateTime()).isEqualTo(updateData.scheduledDateTime());
        assertThat(task.getTimeZoneId()).isEqualTo(updateData.timeZoneId());
        assertThat(task.getModificationDateTime()).isNotNull();

        verify(taskPersister).save(task);
    }

    @Test
    void updateTask_shouldRejectWhenTaskDoesNotExist() {
        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                underTest.updateTask(validTaskData(), TASK_ID))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(ServiceExceptionCode.TASK_NOT_FOUND);
                });

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).save(any());
        verifyNoInteractions(principalProvider);
    }

    @Test
    void updateTask_shouldRejectWhenTaskDoesNotBelongToCurrentUser() {
        Task task = taskOwnedBy(OTHER_USER_ID);

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(principalProvider.getId()).thenReturn(USER_ID);

        assertThatThrownBy(() ->
                underTest.updateTask(validTaskData(), TASK_ID))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(ServiceExceptionCode.NO_TASK_OWNERSHIP);
                });

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).save(any());
    }

    @Test
    void updateTask_shouldNotPersistWhenDomainValidationFails() {
        Task task = taskOwnedBy(USER_ID);

        TaskData invalidData = new TaskData(
                "Updated task",
                "Updated description",
                Instant.now().minusSeconds(1),
                ZoneId.of("Europe/Dublin"));

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(principalProvider.getId()).thenReturn(USER_ID);

        assertThatThrownBy(() ->
                underTest.updateTask(invalidData, TASK_ID))
                .isInstanceOf(ApplicationException.class);

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).save(any());
    }

    // requestTaskNotification

    @Test
    void requestTaskNotification_shouldPublishAndMarkTaskAsDispatchedWhenTaskCanBeNotified() {
        Task task = taskOwnedBy(USER_ID);

        // The Task starts as PENDING, which is expected to be notifiable.
        underTest.requestTaskNotification(task);

        verify(notificationRequestPublisher).publishNotificationRequest(task);
        verify(taskPersister).save(task);

        assertThat(task.getNotificationStatus())
                .isEqualTo(NotificationStatus.DISPATCHED);
    }

    @Test
    void requestTaskNotification_shouldDoNothingWhenTaskCannotBeNotified() {
        Task task = taskOwnedBy(USER_ID);
        task.updateStatus(NotificationStatus.NOTIFIED);

        underTest.requestTaskNotification(task);

        verifyNoInteractions(notificationRequestPublisher);
        verifyNoInteractions(taskPersister);
    }

    // processTaskNotificationCompletion

    @Test
    void processTaskNotificationCompletion_shouldMarkTaskAsNotified() {
        Task task = taskOwnedBy(USER_ID);

        NotificationResultDetails resultDetails =
                new NotificationResultDetails(
                        TASK_ID,
                        NotificationResult.SUCCESS,
                        null);

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));

        underTest.processTaskNotificationCompletion(resultDetails);

        assertThat(task.getNotificationStatus())
                .isEqualTo(NotificationStatus.NOTIFIED);

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister).save(task);
    }

    @Test
    void processTaskNotificationCompletion_shouldRejectWhenTaskDoesNotExist() {
        NotificationResultDetails resultDetails =
                new NotificationResultDetails(
                        TASK_ID,
                        NotificationResult.SUCCESS,
                        null);

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                underTest.processTaskNotificationCompletion(resultDetails))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(ServiceExceptionCode.TASK_NOT_FOUND);
                });

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).save(any());
    }

    // processTaskNotificationFailure

    @Test
    void processTaskNotificationFailure_shouldMarkTaskAsPendingRetryForTemporaryFailure() {
        Task task = taskOwnedBy(USER_ID);

        NotificationResultDetails resultDetails =
                new NotificationResultDetails(
                        TASK_ID,
                        NotificationResult.TEMPORARY_FAILURE,
                        "Temporary error");

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));

        underTest.processTaskNotificationFailure(resultDetails);

        assertThat(task.getNotificationStatus())
                .isEqualTo(NotificationStatus.PENDING_RETRY);

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister).save(task);
    }

    @Test
    void processTaskNotificationFailure_shouldMarkTaskAsFailedForPermanentFailure() {
        Task task = taskOwnedBy(USER_ID);

        NotificationResultDetails resultDetails =
                new NotificationResultDetails(
                        TASK_ID,
                        NotificationResult.PERMANENT_FAILURE,
                        "Permanent error");

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.of(task));

        underTest.processTaskNotificationFailure(resultDetails);

        assertThat(task.getNotificationStatus())
                .isEqualTo(NotificationStatus.FAILED);

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister).save(task);
    }

    @Test
    void processTaskNotificationFailure_shouldRejectWhenTaskDoesNotExist() {
        NotificationResultDetails resultDetails =
                new NotificationResultDetails(
                        TASK_ID,
                        NotificationResult.TEMPORARY_FAILURE,
                        "Temporary error");

        when(taskPersister.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                underTest.processTaskNotificationFailure(resultDetails))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(ServiceExceptionCode.TASK_NOT_FOUND);
                });

        verify(taskPersister).findById(TASK_ID);
        verify(taskPersister, never()).save(any());
    }
}
