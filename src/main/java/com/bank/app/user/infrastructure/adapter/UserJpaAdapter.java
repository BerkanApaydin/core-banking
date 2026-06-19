package com.bank.app.user.infrastructure.adapter;

import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.application.port.SaveUserPort;
import com.bank.app.user.domain.User;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserJpaAdapter implements LoadUserPort, SaveUserPort {

    private final UserRepository userRepository;

    public UserJpaAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toDomain);
    }

    @Override
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User null olamaz");
        }
        userRepository.save(toJpaEntity(user));
    }

    @NonNull
    private User toDomain(@NonNull UserJpaEntity entity) {
        return new User(entity.getId(), entity.getUsername(), entity.getPassword(), entity.getRole());
    }

    @NonNull
    private UserJpaEntity toJpaEntity(@NonNull User domain) {
        return new UserJpaEntity(domain.getId(), domain.getUsername(), domain.getPassword(), domain.getRole());
    }
}
