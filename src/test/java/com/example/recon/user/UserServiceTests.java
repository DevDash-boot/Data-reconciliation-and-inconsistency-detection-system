package com.example.recon.user;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTests {

    @Autowired UserService userService;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.execute("DELETE FROM app_user");
    }

    @Test
    @DisplayName("회원가입하면 USER 권한이고 비밀번호는 BCrypt 해시로 저장된다")
    void registerCreatesUserWithHashedPassword() {
        AppUser saved = userService.register("Alice", "password123");

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(saved.getPassword()).startsWith("$2");
    }

    @Test
    @DisplayName("같은 아이디(대소문자 무시)로 다시 가입하면 409")
    void duplicateUsername() {
        userService.register("alice", "password123");

        assertThatThrownBy(() -> userService.register("ALICE", "password123")).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("규칙에 어긋나는 아이디/비밀번호는 400")
    void invalidInput() {
        assertThatThrownBy(() -> userService.register("a", "password123")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> userService.register("alice", "short")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("로그인용 조회: 권한이 ROLE_USER 로 나오고, 없는 아이디는 예외")
    void userDetails() {
        userService.register("alice", "password123");

        UserDetails d = userDetailsService.loadUserByUsername("Alice");

        assertThat(d.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("ADMIN 은 한 번만 만들어지고 이미 있으면 건드리지 않는다")
    void adminCreatedOnce() {
        assertThat(userService.createAdminIfAbsent("admin", "admin-password")).isTrue();
        assertThat(userService.createAdminIfAbsent("admin", "other-password")).isFalse();

        UserDetails d = userDetailsService.loadUserByUsername("admin");
        assertThat(d.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }
}
