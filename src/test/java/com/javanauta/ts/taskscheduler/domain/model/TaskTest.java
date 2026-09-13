package com.javanauta.ts.taskscheduler.domain.model;

import com.javanauta.ts.taskscheduler.application.data.TaskData;
import com.javanauta.ts.taskscheduler.domain.exception.enums.DomainExceptionCode;
import com.javanauta.ts.taskscheduler.domain.exception.enums.DomainValidationExceptionCode;
import com.javanauta.ts.taskscheduler.domain.model.enums.NotificationStatus;
import com.javanauta.ts.taskscheduler.shared.exception.ApplicationException;
import com.javanauta.ts.taskscheduler.shared.exception.ValidationExceptionDetail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "user@example.com";
    private static final ZoneId TIME_ZONE_ID = ZoneId.of("Europe/Dublin");

    private static TaskData validTaskData() {
        return new TaskData(
                "Test task",
                "Test description",
                Instant.now().plusSeconds(3600),
                TIME_ZONE_ID);
    }

    @Test
    void create_shouldInitializeTaskWithExpectedValuesAndDefaults() {
        TaskData taskData = validTaskData();

        Task task = Task.create(taskData, USER_ID, USER_EMAIL);

        assertThat(task.getId()).isNull();
        assertThat(task.getName()).isEqualTo(taskData.name());
        assertThat(task.getDescription()).isEqualTo(taskData.description());
        assertThat(task.getScheduledDateTime()).isEqualTo(taskData.scheduledDateTime());
        assertThat(task.getUserId()).isEqualTo(USER_ID);
        assertThat(task.getUserEmail()).isEqualTo(USER_EMAIL);
        assertThat(task.getTimeZoneId()).isEqualTo(TIME_ZONE_ID);

        assertThat(task.getCreationDateTime()).isNotNull();
        assertThat(task.getNotificationStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(task.getModificationDateTime()).isNull();
    }

    @Test
    void create_shouldRejectScheduledDateInThePast() {
        TaskData taskData = new TaskData(
                "Test task",
                "Test description",
                Instant.now().minusSeconds(1),
                TIME_ZONE_ID);

        assertThatThrownBy(() -> Task.create(taskData, USER_ID, USER_EMAIL))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(DomainExceptionCode.DOMAIN_VALIDATION_ERROR);

                    assertThat(applicationException.getValidationExceptionDetails())
                            .containsExactly(
                                    new ValidationExceptionDetail(
                                            DomainValidationExceptionCode.SCHEDULED_DATETIME_IN_THE_PAST,
                                            "scheduledDateTime"));
                });
    }

    @Test
    void updateStatus_shouldUpdateStatusAndModificationDateTime() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        task.updateStatus(NotificationStatus.NOTIFIED);

        assertThat(task.getNotificationStatus()).isEqualTo(NotificationStatus.NOTIFIED);
        assertThat(task.getModificationDateTime()).isNotNull();
    }

    @Test
    void updateStatus_shouldNotModifyTaskWhenStatusIsUnchanged() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        task.updateStatus(NotificationStatus.PENDING);

        assertThat(task.getNotificationStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(task.getModificationDateTime()).isNull();
    }

    @Test
    void update_shouldUpdateProvidedFields() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        TaskData updateData = new TaskData(
                "Updated name",
                "Updated description",
                Instant.now().plusSeconds(7200),
                ZoneId.of("America/New_York"));

        task.update(updateData);

        assertThat(task.getName()).isEqualTo("Updated name");
        assertThat(task.getDescription()).isEqualTo("Updated description");
        assertThat(task.getScheduledDateTime()).isEqualTo(updateData.scheduledDateTime());
        assertThat(task.getTimeZoneId()).isEqualTo(updateData.timeZoneId());
        assertThat(task.getModificationDateTime()).isNotNull();
    }

    @Test
    void update_shouldKeepExistingValuesWhenFieldsAreNull() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        String originalName = task.getName();
        String originalDescription = task.getDescription();
        Instant originalScheduledDateTime = task.getScheduledDateTime();
        ZoneId originalTimeZoneId = task.getTimeZoneId();

        task.update(new TaskData(null, null, null, null));

        assertThat(task.getName()).isEqualTo(originalName);
        assertThat(task.getDescription()).isEqualTo(originalDescription);
        assertThat(task.getScheduledDateTime()).isEqualTo(originalScheduledDateTime);
        assertThat(task.getTimeZoneId()).isEqualTo(originalTimeZoneId);
        assertThat(task.getModificationDateTime()).isNotNull();
    }

    @Test
    void update_shouldSetBlankDescriptionToNull() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        task.update(new TaskData(null, "   ", null, null));

        assertThat(task.getDescription()).isNull();
        assertThat(task.getModificationDateTime()).isNotNull();
    }

    @Test
    void update_shouldRejectScheduledDateInThePast() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        TaskData updateData = new TaskData(
                null,
                null,
                Instant.now().minusSeconds(1),
                null);

        assertThatThrownBy(() -> task.update(updateData))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;

                    assertThat(applicationException.getCode())
                            .isEqualTo(DomainExceptionCode.DOMAIN_VALIDATION_ERROR);

                    assertThat(applicationException.getValidationExceptionDetails())
                            .containsExactly(
                                    new ValidationExceptionDetail(
                                            DomainValidationExceptionCode.SCHEDULED_DATETIME_IN_THE_PAST,
                                            "scheduledDateTime"));
                });
    }

    @Test
    void canBeNotified_shouldReturnTrueForNotifiableStatus() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        NotificationStatus notifiableStatus =
                NotificationStatus.notifiableStatuses().iterator().next();

        task.updateStatus(notifiableStatus);

        assertThat(task.canBeNotified()).isTrue();
    }

    @Test
    void canBeNotified_shouldReturnFalseForNonNotifiableStatus() {
        Task task = Task.create(validTaskData(), USER_ID, USER_EMAIL);

        NotificationStatus nonNotifiableStatus =
                stream(NotificationStatus.values())
                        .filter(status -> !NotificationStatus.notifiableStatuses().contains(status))
                        .findFirst()
                        .orElseThrow();

        task.updateStatus(nonNotifiableStatus);

        assertThat(task.canBeNotified()).isFalse();
    }
}
