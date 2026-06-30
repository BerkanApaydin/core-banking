package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.User;
import org.springframework.stereotype.Component;
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
        return repository.findByUsername(username)
                .map(mapper::toDomain);
    }

    @Override
    public void save(User user) {
        UserJpaEntity entity;
        if (user.getId() == null) {
            entity = mapper.toJpaEntity(user);
        } else {
            entity = repository.findById(user.getId().value())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "User not found with id: " + user.getId().value()));
            mapper.updateJpaEntity(entity, user);
        }
        repository.save(entity);
    }
}
