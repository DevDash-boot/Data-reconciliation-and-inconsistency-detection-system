package com.example.recon.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * 권한 정책 (#21)
 *
 * | 대상                                   | 비로그인          | USER | ADMIN |
 * |----------------------------------------|-------------------|------|-------|
 * | /login, /signup, /actuator/health      | 허용              | 허용 | 허용  |
 * | GET /api/**  (조회)                    | 401               | 허용 | 허용  |
 * | POST/PATCH/... /api/** (업로드, 실행, 상태 변경) | 401     | 403  | 허용  |
 * | 화면 (/dashboard, /mismatches ...)     | 로그인 페이지 이동 | 허용 | 허용  |
 *
 * - API(/api/**) 는 CSRF 를 제외하고 HTTP Basic 도 허용한다. (curl -u admin:비밀번호)
 * - 화면은 세션 로그인(폼) + CSRF 를 쓴다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RequestMatcher apiRequest = request -> request.getRequestURI().startsWith("/api/");

        // 비로그인 접근: API 는 401, 화면은 로그인 페이지로 이동
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(apiRequest, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));

        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/login", "/signup", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().hasAnyRole("USER", "ADMIN"))
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/dashboard", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
