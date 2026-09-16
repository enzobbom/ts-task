package com.javanauta.ts.taskscheduler.rabbitmq;

import com.javanauta.ts.events.notification.NotificationRequestEvent;
import com.javanauta.ts.events.notification.messaging.Exchanges;
import com.javanauta.ts.events.notification.messaging.Queues;
import com.javanauta.ts.events.notification.messaging.RoutingKeys;
import com.javanauta.ts.taskscheduler.adapters.out.messaging.RabbitNotificationRequestPublisher;
import com.javanauta.ts.taskscheduler.adapters.shared.config.RabbitCommonConfig;
import com.javanauta.ts.taskscheduler.application.data.TaskData;
import com.javanauta.ts.taskscheduler.domain.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                RabbitNotificationRequestPublisher.class,
                RabbitCommonConfig.class,
                RabbitNotificationRequestPublisherIT.RabbitTestTopology.class
        }
)
@EnableAutoConfiguration
@Testcontainers
class RabbitNotificationRequestPublisherIT {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    private RabbitNotificationRequestPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishNotificationRequest_shouldPublishNotificationRequestEvent() {
        Task task = Task.create(
                new TaskData(
                        "Test task",
                        "Test description",
                        Instant.parse("2099-09-15T12:00:00Z"),
                        ZoneId.of("Europe/Dublin")),
                UUID.randomUUID(),
                "test@example.com");

        task.setId("task-123");

        publisher.publishNotificationRequest(task);

        NotificationRequestEvent event =
                (NotificationRequestEvent) rabbitTemplate.receiveAndConvert(
                        Queues.NOTIFICATION_REQUEST,
                        5_000);

        assertThat(event).isNotNull();
        assertThat(event.taskId()).isEqualTo(task.getId());
        assertThat(event.taskName()).isEqualTo(task.getName());
        assertThat(event.taskDescription()).isEqualTo(task.getDescription());
        assertThat(event.taskScheduledDateTime()).isEqualTo(task.getScheduledDateTime());
        assertThat(event.taskRecipient()).isEqualTo(task.getUserEmail());
        assertThat(event.taskZoneId()).isEqualTo(task.getTimeZoneId().toString());

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @TestConfiguration
    static class RabbitTestTopology {

        @Bean
        TopicExchange notificationExchange() {
            return new TopicExchange(Exchanges.NOTIFICATION);
        }

        @Bean
        Queue notificationRequestQueue() {
            return new Queue(Queues.NOTIFICATION_REQUEST);
        }

        @Bean
        Binding notificationRequestBinding(
                Queue notificationRequestQueue,
                TopicExchange notificationExchange) {

            return BindingBuilder
                    .bind(notificationRequestQueue)
                    .to(notificationExchange)
                    .with(RoutingKeys.NOTIFICATION_REQUEST);
        }
    }
}