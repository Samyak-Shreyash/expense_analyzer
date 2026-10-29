package com.expenseanalyzer.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expenseanalyzer.transaction.model.Transaction;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-database tests (H2 via JPA) for Transaction entity constraints, owner scoping and lifecycle.
 * TransactionRepositoryTest only stubs the repository interface, so it cannot prove any of this.
 * The Transaction entity has no fingerprint field, so fingerprint behaviour is not covered.
 */
@SpringBootTest
@Transactional
class TransactionPersistenceTest {

    private static final UUID ALICE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID BOB = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    private static final Instant DATE = Instant.parse("2026-09-15T12:00:00Z");

    @Autowired private TransactionRepository repository;
    @Autowired private EntityManager entityManager;

    private Transaction transaction(UUID userId, String description, String amount, String type) {
        return new Transaction(userId, description, new BigDecimal(amount), DATE, type);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // ---- Entity constraints ----

    @Test
    void save_shouldAssignIdAndPersistAllRequiredFields() {
        Transaction saved = repository.saveAndFlush(transaction(ALICE, "Coffee", "450.00", "DEBIT"));
        entityManager.clear();

        Transaction reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getUserId()).isEqualTo(ALICE);
        assertThat(reloaded.getRawDescription()).isEqualTo("Coffee");
        assertThat(reloaded.getAmountCents()).isEqualByComparingTo("450.00");
        assertThat(reloaded.getDate()).isEqualTo(DATE);
        assertThat(reloaded.getType()).isEqualTo("DEBIT");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_shouldReject_whenRawDescriptionMissing() {
        assertRejected(t -> t.setRawDescription(null));
    }

    @Test
    void save_shouldReject_whenAmountMissing() {
        assertRejected(t -> t.setAmountCents(null));
    }

    @Test
    void save_shouldReject_whenDateMissing() {
        assertRejected(t -> t.setDate(null));
    }

    @Test
    void save_shouldReject_whenTypeMissing() {
        assertRejected(t -> t.setType(null));
    }

    @Test
    void save_shouldReject_whenUserIdMissing() {
        assertRejected(t -> t.setUserId(null));
    }

    @Test
    void save_shouldReject_whenCreatedAtMissing() {
        assertRejected(t -> t.setCreatedAt(null));
    }

    @Test
    void save_shouldReject_whenRawDescriptionExceedsMaxLength() {
        assertRejected(t -> t.setRawDescription("A".repeat(2049)));
    }

    @Test
    void save_shouldAccept_whenRawDescriptionAtMaxLength() {
        Transaction saved = repository.saveAndFlush(transaction(ALICE, "A".repeat(2048), "1.00", "DEBIT"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void save_shouldAllowOptionalFieldsToBeNull() {
        Transaction t = transaction(ALICE, "No extras", "1.00", "DEBIT");
        t.setNormalizedDescription(null);
        t.setMerchantId(null);
        t.setCategoryId(null);
        t.setStatementId(null);

        assertThat(repository.saveAndFlush(t).getId()).isNotNull();
    }

    @Test
    void save_shouldReject_whenAmountExceedsMaxValue() {
        assertRejected(t -> t.setAmountCents(new BigDecimal("10000000000.00")));
    }

    @Test
    void save_shouldAccept_whenAmountAtMaxValue() {
        Transaction saved = repository.saveAndFlush(transaction(ALICE, "Max", "9999999999.99", "DEBIT"));
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getAmountCents())
                .isEqualByComparingTo("9999999999.99");
    }

    @Test
    void save_shouldPersistAmountWithScaleTwo() {
        Transaction saved = repository.saveAndFlush(transaction(ALICE, "Scale", "12", "DEBIT"));
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getAmountCents().scale()).isEqualTo(2);
    }

    @Test
    void save_shouldAcceptNegativeAmount() {
        Transaction saved = repository.saveAndFlush(transaction(ALICE, "Refund", "-5.00", "CREDIT"));
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getAmountCents())
                .isEqualByComparingTo("-5.00");
    }

    private void assertRejected(Consumer<Transaction> breakIt) {
        Transaction t = transaction(ALICE, "Invalid", "1.00", "DEBIT");
        breakIt.accept(t);

        assertThatThrownBy(() -> repository.saveAndFlush(t)).isInstanceOf(Exception.class);
        entityManager.clear();
    }

    // ---- Owner scoping ----

    @Test
    void findByUserId_shouldNotReturnOtherUsersTransactions() {
        repository.save(transaction(ALICE, "Alice 1", "10.00", "DEBIT"));
        repository.save(transaction(BOB, "Bob 1", "20.00", "DEBIT"));
        flushAndClear();

        List<Transaction> result = repository.findByUserId(ALICE);

        assertThat(result).extracting(Transaction::getRawDescription).containsExactly("Alice 1");
        assertThat(result).allMatch(t -> t.getUserId().equals(ALICE));
    }

    @Test
    void findByUserIdAndStatementId_shouldNotReturnOtherUsersTransactions_forSameStatement() {
        UUID statementId = UUID.randomUUID();
        Transaction alice = transaction(ALICE, "Alice stmt", "10.00", "DEBIT");
        alice.setStatementId(statementId);
        Transaction bob = transaction(BOB, "Bob stmt", "20.00", "DEBIT");
        bob.setStatementId(statementId);
        repository.save(alice);
        repository.save(bob);
        flushAndClear();

        assertThat(repository.findByUserIdAndStatementId(ALICE, statementId))
                .extracting(Transaction::getRawDescription).containsExactly("Alice stmt");
        assertThat(repository.findByUserIdAndStatementId(UUID.randomUUID(), statementId)).isEmpty();
    }





    @Test
    void queries_shouldReturnNothing_forUserWithNoTransactions() {
        repository.save(transaction(ALICE, "Alice", "10.00", "DEBIT"));
        flushAndClear();

        assertThat(repository.findByUserId(BOB)).isEmpty();
    }

    // ---- Lifecycle ----

    @Test
    void lifecycle_shouldCreateReadUpdateAndDelete() {
        Transaction created = transaction(ALICE, "Original", "100.00", "DEBIT");
        Instant createdAt = created.getCreatedAt().minus(1, ChronoUnit.HOURS);
        created.setCreatedAt(createdAt);
        created.setUpdatedAt(createdAt);
        UUID id = repository.saveAndFlush(created).getId();
        entityManager.clear();

        Transaction read = repository.findById(id).orElseThrow();
        assertThat(read.getRawDescription()).isEqualTo("Original");

        UUID categoryId = UUID.randomUUID();
        read.setNormalizedDescription("Normalised");
        read.setCategoryId(categoryId);
        read.setAmountCents(new BigDecimal("125.50"));
        read.onUpdate();
        repository.saveAndFlush(read);
        entityManager.clear();

        Transaction updated = repository.findById(id).orElseThrow();
        assertThat(updated.getNormalizedDescription()).isEqualTo("Normalised");
        assertThat(updated.getCategoryId()).isEqualTo(categoryId);
        assertThat(updated.getAmountCents()).isEqualByComparingTo("125.50");
        assertThat(updated.getRawDescription()).isEqualTo("Original");
        assertThat(updated.getUserId()).isEqualTo(ALICE);
        assertThat(updated.getUpdatedAt()).isAfter(createdAt);

        repository.deleteById(id);
        flushAndClear();
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void update_shouldNotChangeDateOrCreatedAt_becauseColumnsAreNotUpdatable() {
        UUID id = repository.saveAndFlush(transaction(ALICE, "Immutable", "1.00", "DEBIT")).getId();
        entityManager.clear();
        Instant storedCreatedAt = repository.findById(id).orElseThrow().getCreatedAt();
        entityManager.clear();

        Transaction loaded = repository.findById(id).orElseThrow();
        loaded.setDate(DATE.plus(30, ChronoUnit.DAYS));
        loaded.setCreatedAt(Instant.parse("2000-01-01T00:00:00Z"));
        loaded.setRawDescription("Edited");
        repository.saveAndFlush(loaded);
        entityManager.clear();

        Transaction reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getDate()).isEqualTo(DATE);
        assertThat(reloaded.getCreatedAt()).isEqualTo(storedCreatedAt);
        assertThat(reloaded.getRawDescription()).isEqualTo("Edited");
    }
}
