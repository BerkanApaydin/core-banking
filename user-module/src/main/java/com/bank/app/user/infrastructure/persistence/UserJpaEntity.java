package com.bank.app.user.infrastructure.persistence;

import com.bank.app.common.persistence.AuditableJpaEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserJpaEntity extends AuditableJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Column
    private String email;

    @Column
    private String phone;

    public UserJpaEntity() {}

    public UserJpaEntity(Long id, String username, String password, String role) {
        this(id, username, password, role, null, null);
    }

    public UserJpaEntity(Long id, String username, String password, String role, String email, String phone) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
