package com.javanauta.ts.taskscheduler.integration.controller;

import com.javanauta.ts.apicontract.http.HttpHeaders;
import com.javanauta.ts.apicontract.response.enums.ResponseStatus;
import com.javanauta.ts.taskscheduler.adapters.in.security.authentication.ForwardedIdentityFilter;
import com.javanauta.ts.taskscheduler.adapters.in.security.config.SecurityConfig;
import com.javanauta.ts.taskscheduler.adapters.in.security.exception.JwtAuthenticationEntryPoint;
import com.javanauta.ts.taskscheduler.adapters.in.security.exception.enums.SecurityExceptionCode;
import com.javanauta.ts.taskscheduler.adapters.in.web.controller.TaskController;
import com.javanauta.ts.taskscheduler.adapters.in.web.dto.in.CreateTaskRequestDTO;
import com.javanauta.ts.taskscheduler.adapters.in.web.dto.in.UpdateTaskRequestDTO;
import com.javanauta.ts.taskscheduler.adapters.in.web.exception.GlobalExceptionHandler;
import com.javanauta.ts.taskscheduler.adapters.in.web.exception.enums.PresentationExceptionCode;
import com.javanauta.ts.taskscheduler.adapters.in.web.mapper.TaskMapperImpl;
import com.javanauta.ts.taskscheduler.adapters.in.web.path.ApiPaths;
import com.javanauta.ts.taskscheduler.application.data.TaskData;
import com.javanauta.ts.taskscheduler.application.exception.enums.ServiceExceptionCode;
import com.javanauta.ts.taskscheduler.application.service.TaskService;
import com.javanauta.ts.taskscheduler.domain.model.Task;
import com.javanauta.ts.taskscheduler.domain.model.enums.NotificationStatus;
import com.javanauta.ts.taskscheduler.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = {
                TaskController.class,
                TaskMapperImpl.class,
                SecurityConfig.class,
                ForwardedIdentityFilter.class,
                JwtAuthenticationEntryPoint.class,
                GlobalExceptionHandler.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@EnableAutoConfiguration
class TaskControllerIT {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "user@example.com";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TaskService taskService;

    private Task task;

    @BeforeEach
    void setUp() {
        task = mock(Task.class);

        when(task.getId()).thenReturn("task-123");
        when(task.getName()).thenReturn("Test task");
        when(task.getDescription()).thenReturn("Test description");
        when(task.getCreationDateTime()).thenReturn(Instant.parse("2026-09-15T10:00:00Z"));
        when(task.getScheduledDateTime()).thenReturn(Instant.parse("2026-09-16T10:00:00Z"));
        when(task.getUserId()).thenReturn(USER_ID);
        when(task.getUserEmail()).thenReturn(USER_EMAIL);
        when(task.getModificationDateTime()).thenReturn(Instant.parse("2026-09-15T11:00:00Z"));
        when(task.getNotificationStatus()).thenReturn(NotificationStatus.PENDING);
        when(task.getTimeZoneId()).thenReturn(ZoneId.of("Europe/Dublin"));
    }

    @Test
    void shouldCreateTaskWhenAuthenticated() {
        CreateTaskRequestDTO request = CreateTaskRequestDTO.builder()
                .name("Test task")
                .description("Test description")
                .scheduledDateTime(Instant.parse("2026-09-16T10:00:00Z"))
                .timeZoneId("Europe/Dublin")
                .build();

        when(taskService.createTask(any(TaskData.class))).thenReturn(task);

        webTestClient.post()
                .uri(ApiPaths.TASKS_V1)
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.SUCCESS.toString())
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").isEqualTo("task-123")
                .jsonPath("$.data.name").isEqualTo("Test task")
                .jsonPath("$.data.description").isEqualTo("Test description")
                .jsonPath("$.data.userId").isEqualTo(USER_ID.toString())
                .jsonPath("$.data.userEmail").isEqualTo(USER_EMAIL)
                .jsonPath("$.data.notificationStatus").isEqualTo(NotificationStatus.PENDING.toString())
                .jsonPath("$.data.timeZoneId").isEqualTo("Europe/Dublin");

        verify(taskService).createTask(any(TaskData.class));
    }

    @Test
    void shouldReturnUnauthorizedWhenCreatingTaskWithoutAuthentication() {
        CreateTaskRequestDTO request = CreateTaskRequestDTO.builder()
                .name("Test task")
                .description("Test description")
                .scheduledDateTime(Instant.parse("2026-09-16T10:00:00Z"))
                .timeZoneId("Europe/Dublin")
                .build();

        webTestClient.post()
                .uri(ApiPaths.TASKS_V1)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.ERROR.toString())
                .jsonPath("$.code").isEqualTo(401)
                .jsonPath("$.errorCode").isEqualTo(
                        SecurityExceptionCode.AUTHENTICATION_ERROR.getIdentifier()
                )
                .jsonPath("$.validationErrors").isArray();

        verifyNoInteractions(taskService);
    }

    @Test
    void shouldReturnUnprocessableContentWhenCreateRequestIsInvalid() {
        CreateTaskRequestDTO request = CreateTaskRequestDTO.builder()
                .name("")
                .scheduledDateTime(null)
                .timeZoneId("invalid-time-zone")
                .build();

        webTestClient.post()
                .uri(ApiPaths.TASKS_V1)
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.ERROR.toString())
                .jsonPath("$.code").isEqualTo(422)
                .jsonPath("$.errorCode").isEqualTo(
                        PresentationExceptionCode.REQUEST_BODY_VIOLATION_ERROR.getIdentifier()
                )
                .jsonPath("$.validationErrors").isArray();

        verifyNoInteractions(taskService);
    }

    @Test
    void shouldGetTasksWhenAuthenticated() {
        when(taskService.getTasks()).thenReturn(List.of(task));

        webTestClient.get()
                .uri(ApiPaths.TASKS_V1)
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.SUCCESS.toString())
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].id").isEqualTo("task-123")
                .jsonPath("$.data[0].name").isEqualTo("Test task")
                .jsonPath("$.data[0].userId").isEqualTo(USER_ID.toString())
                .jsonPath("$.data[0].timeZoneId").isEqualTo("Europe/Dublin");

        verify(taskService).getTasks();
    }

    @Test
    void shouldDeleteTaskWhenAuthenticated() {
        webTestClient.delete()
                .uri(ApiPaths.TASKS_V1 + "/task-123")
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(taskService).deleteTask("task-123");
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistingTask() {
        doThrow(new ApplicationException(ServiceExceptionCode.TASK_NOT_FOUND, "Task not found"))
                .when(taskService)
                .deleteTask("task-123");

        webTestClient.delete()
                .uri(ApiPaths.TASKS_V1 + "/task-123")
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.ERROR.toString())
                .jsonPath("$.code").isEqualTo(404)
                .jsonPath("$.errorCode").isEqualTo(
                        ServiceExceptionCode.TASK_NOT_FOUND.getIdentifier()
                );

        verify(taskService).deleteTask("task-123");
    }

    @Test
    void shouldUpdateTaskWhenAuthenticated() {
        UpdateTaskRequestDTO request = UpdateTaskRequestDTO.builder()
                .name("Updated task")
                .description("Updated description")
                .scheduledDateTime(Instant.parse("2026-09-17T10:00:00Z"))
                .timeZoneId("Europe/Dublin")
                .build();

        when(taskService.updateTask(any(TaskData.class), eq("task-123"))).thenReturn(task);

        webTestClient.patch()
                .uri(ApiPaths.TASKS_V1 + "/task-123")
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.SUCCESS.toString())
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").isEqualTo("task-123")
                .jsonPath("$.data.name").isEqualTo("Test task")
                .jsonPath("$.data.timeZoneId").isEqualTo("Europe/Dublin");

        verify(taskService).updateTask(any(TaskData.class), eq("task-123"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformed() {
        webTestClient.post()
                .uri(ApiPaths.TASKS_V1)
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "name": "Test task",
                            "scheduledDateTime":
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.ERROR.toString())
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.errorCode").isEqualTo(
                        PresentationExceptionCode.JSON_PARSE_ERROR.getIdentifier()
                )
                .jsonPath("$.validationErrors").isArray();

        verifyNoInteractions(taskService);
    }

    @Test
    void shouldReturnForbiddenWhenDeletingTaskNotOwnedByUser() {
        doThrow(new ApplicationException(ServiceExceptionCode.NO_TASK_OWNERSHIP, "User does not own the requested task"))
                .when(taskService)
                .deleteTask("task-123");

        webTestClient.delete()
                .uri(ApiPaths.TASKS_V1 + "/task-123")
                .header(HttpHeaders.USER_ID, USER_ID.toString())
                .header(HttpHeaders.USER_EMAIL, USER_EMAIL)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(ResponseStatus.ERROR.toString())
                .jsonPath("$.code").isEqualTo(403)
                .jsonPath("$.errorCode").isEqualTo(
                        ServiceExceptionCode.NO_TASK_OWNERSHIP.getIdentifier()
                );

        verify(taskService).deleteTask("task-123");
    }
}
