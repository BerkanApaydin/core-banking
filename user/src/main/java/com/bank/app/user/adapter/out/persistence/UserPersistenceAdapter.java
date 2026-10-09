package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.UsernameAlreadyTakenException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.Optional;

@Component
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

    private final UserJpaRepository repository;
    private final UserJpaMapper mapper;

    public UserPersistenceAdapter(UserJpaRepository repository, UserJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        try {
            return repository.findByUsername(username).map(mapper::toDomain);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public Optional<User> findById(Long userId) {
        if (userId == null) return Optional.empty();
        try {
            return repository.findById(userId).map(mapper::toDomain);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public User save(User user) {
        // AV-4: the unique-constraint translation lives here (adapter), not in
        // the application layer — RegisterUserUseCaseImpl must stay free of
        // Spring DAO imports (see LayeringArchitectureTest).
        try {
            UserJpaEntity entity;
            if (user.getId() == null) {
                entity = mapper.toJpaEntity(user);
            } else {
                entity = repository.findById(user.getId().value())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "User not found with id: " + user.getId().value()));
                if (user.getVersion() == null || !user.getVersion().equals(entity.getVersion())) {
                    throw new ObjectOptimisticLockingFailureException(UserJpaEntity.class, user.getId().value());
                }
                mapper.updateJpaEntity(entity, user);
            }
            return mapper.toDomain(repository.save(entity));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Check-then-save TOCTOU: two concurrent registrations with the same
            // username both pass the findByUsername guard; the DB unique
            // constraint is the final arbiter — translate it back to the domain
            // conflict instead of leaking a Spring exception outward.
            String username = user.getUsername() != null ? user.getUsername() : "?";
            throw new UsernameAlreadyTakenException(username);
        }
    }
}
