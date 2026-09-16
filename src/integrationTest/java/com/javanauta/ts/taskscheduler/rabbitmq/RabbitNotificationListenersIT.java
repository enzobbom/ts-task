package com.javanauta.ts.taskscheduler.rabbitmq;

import com.javanauta.ts.events.notification.NotificationCompletedEvent;
import com.javanauta.ts.events.notification.NotificationFailedEvent;
import com.javanauta.ts.events.notification.enums.NotificationFailureType;
import com.javanauta.ts.events.notification.messaging.Exchanges;
import com.javanauta.ts.events.notification.messaging.Queues;
import com.javanauta.ts.events.notification.messaging.RoutingKeys;
import com.javanauta.ts.taskscheduler.adapters.in.messaging.NotificationEventRecoverer;
import com.javanauta.ts.taskscheduler.adapters.in.messaging.RabbitNotificationCompletedListener;
import com.javanauta.ts.taskscheduler.adapters.in.messaging.RabbitNotificationFailedListener;
import com.javanauta.ts.taskscheduler.adapters.in.messaging.config.RabbitInConfig;
import com.javanauta.ts.taskscheduler.adapters.in.messaging.validation.NotificationEventValidator;
import com.javanauta.ts.taskscheduler.adapters.shared.config.RabbitCommonConfig;
import com.javanauta.ts.taskscheduler.application.data.enums.NotificationResult;
import com.javanauta.ts.taskscheduler.application.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {
                RabbitNotificationCompletedListener.class,
                RabbitNotificationFailedListener.class,
                NotificationEventValidator.class,
                NotificationEventRecoverer.class,
                RabbitCommonConfig.class,
                RabbitInConfig.class,
                RabbitNotificationListenersIT.RabbitTestTopology.class
        }
)
@EnableAutoConfiguration
@EnableRabbit
@Testcontainers
class RabbitNotificationListenersIT {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:4-management");

    @MockitoBean
    private TaskService taskService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void completedEvent_shouldProcessNotificationCompletion() {
        String taskId = "task-123";

        NotificationCompletedEvent event =
                NotificationCompletedEvent.create(taskId);

        rabbitTemplate.convertAndSend(
                Exchanges.NOTIFICATION,
                RoutingKeys.NOTIFICATION_COMPLETED,
                event);

        // Wait until the listener has consumed the message.
        await().untilAsserted(() ->
                verify(taskService).processTaskNotificationCompletion(
                        argThat(result ->
                                result.taskId().equals(taskId)
                                        && result.notificationResult() == NotificationResult.SUCCESS
                                        && result.errorMessage() == null)));
    }

    @Test
    void temporaryFailureEvent_shouldProcessTemporaryFailure() {
        String taskId = "task-123";
        String error = "SMTP server temporarily unavailable";

        NotificationFailedEvent event =
                NotificationFailedEvent.create(
                        taskId,
                        NotificationFailureType.TEMPORARY,
                        error);

        rabbitTemplate.convertAndSend(
                Exchanges.NOTIFICATION,
                RoutingKeys.NOTIFICATION_FAILED,
                event);

        await().untilAsserted(() ->
                verify(taskService).processTaskNotificationFailure(
                        argThat(result ->
                                result.taskId().equals(taskId)
                                        && result.notificationResult() == NotificationResult.TEMPORARY_FAILURE
                                        && result.errorMessage().equals(error))));
    }

    @Test
    void permanentFailureEvent_shouldProcessPermanentFailure() {
        String taskId = "task-123";
        String error = "Invalid recipient address";

        NotificationFailedEvent event =
                NotificationFailedEvent.create(
                        taskId,
                        NotificationFailureType.PERMANENT,
                        error);

        rabbitTemplate.convertAndSend(
                Exchanges.NOTIFICATION,
                RoutingKeys.NOTIFICATION_FAILED,
                event);

        await().untilAsserted(() ->
                verify(taskService).processTaskNotificationFailure(
                        argThat(result ->
                                result.taskId().equals(taskId)
                                        && result.notificationResult() == NotificationResult.PERMANENT_FAILURE
                                        && result.errorMessage().equals(error))));
    }

    @TestConfiguration
    static class RabbitTestTopology {

        @Bean
        TopicExchange notificationExchange() {
            return new TopicExchange(Exchanges.NOTIFICATION);
        }

        @Bean
        Queue notificationCompletedQueue() {
            return new Queue(Queues.NOTIFICATION_COMPLETED);
        }

        @Bean
        Binding notificationCompletedBinding(
                Queue notificationCompletedQueue,
                TopicExchange notificationExchange) {

            return BindingBuilder
                    .bind(notificationCompletedQueue)
                    .to(notificationExchange)
                    .with(RoutingKeys.NOTIFICATION_COMPLETED);
        }

        @Bean
        Queue notificationFailedQueue() {
            return new Queue(Queues.NOTIFICATION_FAILED);
        }

        @Bean
        Binding notificationFailedBinding(
                Queue notificationFailedQueue,
                TopicExchange notificationExchange) {

            return BindingBuilder
                    .bind(notificationFailedQueue)
                    .to(notificationExchange)
                    .with(RoutingKeys.NOTIFICATION_FAILED);
        }
    }
}
