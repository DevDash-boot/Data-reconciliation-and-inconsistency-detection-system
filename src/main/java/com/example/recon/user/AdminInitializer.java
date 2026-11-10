package com.example.recon.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 시작할 때 ADMIN 계정을 만든다. (.env 의 ADMIN_USERNAME / ADMIN_PASSWORD)
 * 값이 없으면 아무것도 하지 않는다. 이미 있는 계정은 건드리지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements ApplicationRunner {

    private final UserService userService;

    @Value("${recon.admin.username:}")
    private String username;

    @Value("${recon.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            log.info("ADMIN 계정 설정(ADMIN_USERNAME / ADMIN_PASSWORD)이 없어 건너뜁니다");
            return;
        }
        if (userService.createAdminIfAbsent(username, password)) {
            log.info("ADMIN 계정 '{}' 을 만들었습니다", username);
        }
    }
}
