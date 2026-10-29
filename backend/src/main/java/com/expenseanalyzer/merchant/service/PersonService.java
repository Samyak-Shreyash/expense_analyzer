package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.merchant.dto.PersonRequest;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped people. Another user's person is indistinguishable from a missing one (404). */
@Service
@RequiredArgsConstructor
public class PersonService {

    private static final int MAX_NAME_LENGTH = 100;

    private final PersonRepository personRepository;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public List<Person> list(String email) {
        return personRepository.findByUserIdOrderByNameAsc(categoryService.userId(email));
    }

    @Transactional
    public Person update(String email, UUID id, PersonRequest request) {
        UUID userId = categoryService.userId(email);
        Person person = personRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

    /** Find-or-create the user's person by name (case-insensitive). The name must already be valid. */
    @Transactional
    public Person findOrCreate(UUID userId, String name) {
        return personRepository.findByUserIdAndNameIgnoreCase(userId, name)
                .orElseGet(() -> personRepository.save(new Person(userId, name)));
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
