---
name: java-unit-test
description: Generate JUnit 5 unit tests for a Java class in the Expense Analyzer backend. Use when asked to add or improve tests for a class or method, or when a service, controller, or repository class lacks corresponding tests.
argument-hint: "[path to the Java class under backend/src/main/java]"
---

# Generate Unit Tests for a Java Class

## When to use this skill

- User asks to "add tests" or "write unit tests" for a Java class.
- A class under `backend/src/main/java/com/expenseanalyzer/<feature>/` has no
  corresponding test file.
- User asks to improve coverage or cover edge cases for an existing class.
- User asks to reproduce a bug with a failing test before fixing it.

## Repository context

- Source root: `backend/src/main/java/com/expenseanalyzer/`
- Test root:   `backend/src/test/java/com/expenseanalyzer/`
- Package layout per feature: `controller/`, `dto/`, `domain/`, `repository/`,
  `service/`. Test packages mirror the production packages exactly.
- Feature packages: `account`, `ai`, `analytics`, `auth`, `category`, `enrichment`,
  `insight`, `merchant`, `messaging`, `notification`, `processing`, `statement`,
  `transaction`, `user`. Shared code lives in `common`.
- Build tool: Gradle Groovy DSL. Run tests from `backend/` on Windows:
  `./gradlew.bat test`
- Test profile uses H2. Do not introduce Testcontainers, embedded Postgres, or other
  test tooling unless the task explicitly adds the dependency to `build.gradle`.

## Procedure

1. **Read the target class.** Identify its public methods, constructor dependencies,
   and the layers it belongs to (`service`, `controller`, `repository`, or `domain`).

2. **Determine the test type** based on the layer:
   - `service/` → plain unit test with Mockito for dependencies.
   - `controller/` → `@WebMvcTest` with `@MockBean` for the service, or a plain unit
     test if the controller has no Spring wiring to verify.
   - `repository/` → `@DataJpaTest` against the H2 test profile. Only add this if the
     class is a Spring Data JPA repository with custom queries.
   - `domain/` → plain unit test, no Spring context.

3. **Locate or create the test file.** Mirror the production package:
   - Production: `backend/src/main/java/com/expenseanalyzer/<feature>/service/Foo.java`
   - Test:       `backend/src/test/java/com/expenseanalyzer/<feature>/service/FooTest.java`
   If a test file already exists, add to it instead of overwriting.

4. **Set up dependencies with Mockito.** Use `@ExtendWith(MockitoExtension.class)`,
   `@Mock` for each dependency, and `@InjectMocks` for the class under test.

5. **Write tests following Arrange-Act-Assert.** One behavior per test. Name tests
   `<method>_should<Expected>_when<Condition>`.

6. **Cover, at minimum:**
   - Happy path with valid input.
   - Null input → `IllegalArgumentException` or the type the class actually throws.
   - Empty collections or empty strings where relevant.
   - Boundary values (`BigDecimal.ZERO`, negative amounts, max lengths).
   - Exception propagation from a mocked dependency.
   - Ownership enforcement for any user-scoped read or write (see repo rules).

7. **Use JUnit 5 + AssertJ + Mockito together:**
   - JUnit 5 annotations: `@Test`, `@BeforeEach`, `@DisplayName`, `@Nested`.
   - AssertJ assertions: `assertThat(result).isEqualTo(...)`,
     `assertThatThrownBy(() -> ...).isInstanceOf(...)`.
   - Mockito for mocks: `when(...).thenReturn(...)`, `verify(...)`,
     `ArgumentCaptor` when verifying payloads.
   - Prefer `assertThatThrownBy` over `assertThrows` so assertions stay in AssertJ.

8. **Never log or assert on sensitive data.** Do not include account numbers,
   transaction descriptions, amounts, or PII in test fixtures or assertion messages.
   Use obviously fake values.

## Example — service unit test

```java
package com.expenseanalyzer.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.transaction.domain.Transaction;
import com.expenseanalyzer.transaction.dto.CreateTransactionRequest;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void create_shouldPersistTransaction_whenRequestIsValid() {
        // Arrange
        var request = new CreateTransactionRequest("user-1", new BigDecimal("12.34"));
        var saved = new Transaction("user-1", new BigDecimal("12.34"));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

        // Act
        var result = transactionService.create(request);

        // Assert
        assertThat(result.amount()).isEqualByComparingTo("12.34");

        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
    }

    @Test
    void create_shouldThrow_whenRequestIsNull() {
        assertThatThrownBy(() -> transactionService.create(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findById_shouldReturnEmpty_whenTransactionNotFound() {
        when(transactionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(transactionService.findById("missing")).isEmpty();
    }
}
```

## Example — controller slice test

```java
package com.expenseanalyzer.transaction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.transaction.dto.CreateTransactionRequest;
import com.expenseanalyzer.transaction.dto.TransactionResponse;
import com.expenseanalyzer.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    void create_shouldReturn200_whenRequestIsValid() throws Exception {
        var request = new CreateTransactionRequest("user-1", new BigDecimal("12.34"));
        when(transactionService.create(any()))
            .thenReturn(new TransactionResponse("tx-1", new BigDecimal("12.34")));

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
}
```

## Run tests

From `backend/` on Windows:

```bat
gradlew.bat test
```

To run a single test class:

```bat
gradlew.bat test --tests "com.expenseanalyzer.transaction.service.TransactionServiceTest"
```

## Quality checks

- Test file lives under `backend/src/test/java/com/expenseanalyzer/<feature>/...` and
  mirrors the production package.
- Uses JUnit 5 (`org.junit.jupiter.*`), AssertJ (`org.assertj.core.api.Assertions.*`),
  and Mockito (`org.mockito.*`). Do not mix in JUnit 4.
- One behavior per test; test names describe scenario and expected outcome.
- No `Thread.sleep`, no ordering dependencies between tests, no shared mutable state.
- No real database, network, or filesystem access in a plain unit test.
- No PII, account numbers, transaction descriptions, or real amounts in fixtures.
- Tests compile and pass with `gradlew.bat test` from `backend/`.