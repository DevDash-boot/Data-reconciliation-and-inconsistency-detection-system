package com.example.recon.user;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 회원가입은 항상 USER 권한이다. ADMIN 은 설정(환경변수)으로만 만들 수 있다. */
    @Transactional
    public AppUser register(String username, String password) {
        return create(username, password, Role.USER);
    }

    /** 이미 있으면 아무것도 하지 않는다. 만들었으면 true. */
    @Transactional
    public boolean createAdminIfAbsent(String username, String password) {
        if (userRepository.existsByUsername(UserInputRules.normalizeUsername(username))) {
            return false;
        }
        create(username, password, Role.ADMIN);
        return true;
    }

    private AppUser create(String username, String password, Role role) {
        try {
            UserInputRules.validateUsername(username);
            UserInputRules.validatePassword(password);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        String name = UserInputRules.normalizeUsername(username);
        if (userRepository.existsByUsername(name)) {
            throw new ConflictException("이미 사용 중인 아이디입니다");
        }
        return userRepository.save(AppUser.builder()
                .username(name)
                .password(passwordEncoder.encode(password))
                .role(role)
                .build());
    }
}
