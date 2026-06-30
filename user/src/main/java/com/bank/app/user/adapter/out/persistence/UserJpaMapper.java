package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserJpaMapper {

    public UserJpaEntity toJpaEntity(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        return new UserJpaEntity(
                user.getId() != null ? user.getId().value() : null,
                user.getUsername(),
                user.getPassword(),
                user.getRole().name(),
                user.getEmail() != null ? user.getEmail().value() : null,
                user.getPhone() != null ? user.getPhone().value() : null,
                user.getVersion()
        );
    }

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        Role role = Role.fromString(entity.getRole());
        EmailAddress email = entity.getEmail() != null ? new EmailAddress(entity.getEmail()) : null;
        PhoneNumber phone = entity.getPhone() != null ? new PhoneNumber(entity.getPhone()) : null;
        return new User(
                new UserId(entity.getId()),
                entity.getUsername(),
                entity.getPassword(),
                role,
                email,
                phone,
                entity.getVersion()
        );
    }

    public void updateJpaEntity(UserJpaEntity entity, User user) {
        if (entity == null || user == null) {
            throw new IllegalArgumentException("Entity and User must not be null");
        }
        entity.setUsername(user.getUsername());
        entity.setPassword(user.getPassword());
        entity.setRole(user.getRole().name());
        entity.setEmail(user.getEmail() != null ? user.getEmail().value() : null);
        entity.setPhone(user.getPhone() != null ? user.getPhone().value() : null);
        entity.setVersion(user.getVersion());
    }
}
