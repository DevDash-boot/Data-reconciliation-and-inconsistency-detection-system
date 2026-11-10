package com.example.recon.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(UserInputRules.normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다"));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())   // ROLE_ADMIN / ROLE_USER
                .build();
    }
}
