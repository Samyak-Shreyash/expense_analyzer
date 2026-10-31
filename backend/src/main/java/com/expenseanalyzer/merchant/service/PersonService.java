package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.merchant.dto.PersonRequest;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.model.PersonPayee;
import com.expenseanalyzer.merchant.repository.PersonPayeeRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner-scoped people. Another user's person is indistinguishable from a missing one (404). Several payee names can
 * group under one person (see {@link PersonPayee}); grouping is always undoable by moving a payee back out.
 */
@Service
@RequiredArgsConstructor
public class PersonService {

    private static final int MAX_NAME_LENGTH = 100;

    private final PersonRepository personRepository;
    private final PersonPayeeRepository payeeRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public List<Person> list(String email) {
        return personRepository.findByUserIdOrderByNameAsc(categoryService.userId(email));
    }

    /** The payee names grouped under each of the caller's people, sorted. */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> payees(String email) {
        return payeeRepository.findByUserId(categoryService.userId(email)).stream()
                .sorted((a, b) -> a.getPayeeKey().compareTo(b.getPayeeKey()))
                .collect(Collectors.groupingBy(PersonPayee::getPersonId,
                        Collectors.mapping(PersonPayee::getPayeeKey, Collectors.toList())));
    }

    @Transactional
    public Person update(String email, UUID id, PersonRequest request) {
        UUID userId = categoryService.userId(email);
        Person person = owned(userId, id);
        String name = validName(request == null ? null : request.name());
        personRepository.findByUserIdAndNameIgnoreCase(userId, name).filter(p -> !p.getId().equals(id))
                .ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Person name already exists");
                });
        if (request.categoryId() != null) {
            categoryService.owned(userId, request.categoryId());
        }
        person.setName(name);
        person.setCategoryId(request.categoryId());
        return person;
    }

    /** Sets (or, with null, clears) the person's money-in ({@code credit}) or money-out default category. */
    @Transactional
    public Person setCategory(String email, UUID id, UUID categoryId, boolean credit) {
        UUID userId = categoryService.userId(email);
        Person person = owned(userId, id);
        if (categoryId != null) {
            categoryService.owned(userId, categoryId);
        }
        if (credit) {
            person.setCreditCategoryId(categoryId);
        } else {
            person.setCategoryId(categoryId);
        }
        return person;
    }

    /**
     * Find-or-create the user's person for a payee name (case-insensitive): the person the name is grouped under, else
     * the person with that name. The name must already be valid; it is remembered as one of the person's payees.
     */
    @Transactional
    public Person findOrCreate(UUID userId, String name) {
        PersonPayee.Key key = new PersonPayee.Key(userId, payeeKey(name));
        Person person = payeeRepository.findById(key).flatMap(p -> personRepository.findById(p.getPersonId()))
                .or(() -> personRepository.findByUserIdAndNameIgnoreCase(userId, name))
                .orElseGet(() -> personRepository.save(new Person(userId, name)));
        if (!payeeRepository.existsById(key)) {
            payeeRepository.save(new PersonPayee(userId, key.payeeKey(), person.getId()));
        }
        return person;
    }

    /**
     * Merges a person into another: its transactions and payee names move over, its default category carries over
     * when the target has none, and it is deleted. Undone by moving the payees back out. Same person: 400.
     */
    @Transactional
    public void merge(String email, UUID id, UUID intoId) {
        UUID userId = categoryService.userId(email);
        Person from = owned(userId, id);
        Person into = owned(userId, intoId);
        if (from.getId().equals(into.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a different person");
        }
        if (into.getCategoryId() == null) {
            into.setCategoryId(from.getCategoryId());
        }
        if (into.getCreditCategoryId() == null) {
            into.setCreditCategoryId(from.getCreditCategoryId());
        }
        personRepository.saveAndFlush(into);
        transactionRepository.reassignPerson(userId, from.getId(), into.getId());
        payeeRepository.repoint(from.getId(), into.getId());
        personRepository.deleteById(from.getId());
    }

    /**
     * Moves one payee name of a person, with that person's transfers to it, to another person or (with a null
     * {@code toId}) to a person of its own, found or created by the payee's name. Not one of the person's payees: 404.
     */
    @Transactional
    public void movePayee(String email, UUID id, String payee, UUID toId) {
        UUID userId = categoryService.userId(email);
        Person from = owned(userId, id);
        String key = payee == null ? "" : payeeKey(payee);
        PersonPayee row = payeeRepository.findById(new PersonPayee.Key(userId, key))
                .filter(p -> p.getPersonId().equals(from.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String ownName = PersonNameParser.titleCase(key);
        Person to = toId != null ? owned(userId, toId) : personRepository.findByUserIdAndNameIgnoreCase(userId, ownName)
                .orElseGet(() -> personRepository.save(new Person(userId, ownName)));
        row.setPersonId(to.getId());
        payeeRepository.save(row);
        Instant now = Instant.now();
        List<Transaction> moved = transactionRepository.findByUserIdAndPersonId(userId, from.getId()).stream()
                .filter(t -> PersonNameParser.parse(t.getRawDescription()).map(PersonService::payeeKey)
                        .filter(key::equals).isPresent())
                .toList();
        moved.forEach(t -> {
            t.setPersonId(to.getId());
            t.setUpdatedAt(now);
        });
        transactionRepository.saveAll(moved);
    }

    private Person owned(UUID userId, UUID id) {
        return personRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Upper-cased, whitespace-collapsed payee name: how payee names are matched. */
    static String payeeKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Trimmed, whitespace-collapsed name, or 400 when blank or too long. */
    public static String validName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }
}
