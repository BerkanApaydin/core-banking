package com.bank.app.user.adapter.out.security;

import com.bank.app.infrastructure.adapter.out.security.CustomUserDetails;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserDetailsAdapter implements UserDetailsService {

    private final LoadUserPort loadUserPort;

    public UserDetailsAdapter(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = loadUserPort.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new CustomUserDetails(
                user.getId().value(),
                user.getUsername(),
                user.getPassword(),
                new ArrayList<>()
        );
    }
}
