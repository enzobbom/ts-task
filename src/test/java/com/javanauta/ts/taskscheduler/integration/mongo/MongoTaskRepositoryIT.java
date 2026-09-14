package com.javanauta.ts.taskscheduler.integration.mongo;

import com.javanauta.ts.taskscheduler.adapters.out.persistence.MongoTaskRepository;
import com.javanauta.ts.taskscheduler.adapters.out.persistence.config.MongoConfig;
import com.javanauta.ts.taskscheduler.application.data.TaskData;
import com.javanauta.ts.taskscheduler.domain.model.Task;
import com.javanauta.ts.taskscheduler.domain.model.enums.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;


import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoConfig.class)
@Testcontainers
class MongoTaskRepositoryIT {
    private static final Instant INITIAL_DATE_TIME = Instant.parse("2099-09-13T10:00:00Z");
    private static final Instant FINAL_DATE_TIME = Instant.parse("2099-09-13T11:00:00Z");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    @Autowired
    private MongoTaskRepository taskRepository;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    @Test
    void findByUserId_shouldReturnOnlyTasksBelongingToUser() {
        UUID userId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();

        taskRepository.save(taskForUser(userId));
        taskRepository.save(taskForUser(userId));
        taskRepository.save(taskForUser(anotherUserId));

        List<Task> result = taskRepository.findByUserId(userId);

        assertThat(result)
                .hasSize(2)
                .allMatch(task -> task.getUserId().equals(userId));
    }

    @Test
    void findByNotificationStatusInAndScheduledDateTimeBetween_shouldReturnNotifiableTaskInsideRange() {
        Instant initialDateTime = INITIAL_DATE_TIME;
        Instant finalDateTime = FINAL_DATE_TIME;

        Task task = taskAt(
                initialDateTime.plus(30, ChronoUnit.MINUTES),
                NotificationStatus.PENDING);

        taskRepository.save(task);

        List<Task> result =
                taskRepository.findByNotificationStatusInAndScheduledDateTimeBetween(
                        NotificationStatus.notifiableStatuses(),
                        initialDateTime,
                        finalDateTime);

        assertThat(result)
                .hasSize(1)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("creationDateTime")
                .containsExactly(task)
                .allMatch(t -> t.getCreationDateTime().equals(truncateToMillis(task.getCreationDateTime())));
    }

    @Test
    void findByNotificationStatusInAndScheduledDateTimeBetween_shouldExcludeTaskOutsideRange() {
        Instant initialDateTime = INITIAL_DATE_TIME;
        Instant finalDateTime = FINAL_DATE_TIME;

        Task task = taskAt(
                finalDateTime.plus(1, ChronoUnit.MINUTES),
                NotificationStatus.PENDING);

        taskRepository.save(task);

        List<Task> result =
                taskRepository.findByNotificationStatusInAndScheduledDateTimeBetween(
                        NotificationStatus.notifiableStatuses(),
                        initialDateTime,
                        finalDateTime);

        assertThat(result).isEmpty();
    }

    @Test
    void findByNotificationStatusInAndScheduledDateTimeBetween_shouldExcludeNonNotifiableTask() {
        Instant initialDateTime = INITIAL_DATE_TIME;
        Instant finalDateTime = FINAL_DATE_TIME;

        Task task = taskAt(
                initialDateTime.plus(30, ChronoUnit.MINUTES),
                NotificationStatus.NOTIFIED);

        taskRepository.save(task);

        List<Task> result =
                taskRepository.findByNotificationStatusInAndScheduledDateTimeBetween(
                        NotificationStatus.notifiableStatuses(),
                        initialDateTime,
                        finalDateTime);

        assertThat(result).isEmpty();
    }

    private Task taskForUser(UUID userId) {
        return taskAt(
                Instant.parse("2099-09-13T10:30:00.000Z"),
                NotificationStatus.PENDING,
                userId);
    }

    private Task taskAt(
            Instant scheduledDateTime,
            NotificationStatus status) {

        return taskAt(scheduledDateTime, status, UUID.randomUUID());
    }

    private Task taskAt(
            Instant scheduledDateTime,
            NotificationStatus status,
            UUID userId) {

        Task task = Task.create(
                new TaskData(
                        "Test task",
                        "Test description",
                        scheduledDateTime,
                        ZoneId.of("UTC")),
                userId,
                "test@example.com");

        task.updateStatus(status);

        return task;
    }

    private Instant truncateToMillis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }
}
