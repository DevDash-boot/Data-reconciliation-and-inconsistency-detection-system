package com.example.recon.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 권한 정책 표(SecurityConfig 주석)를 그대로 검증한다.
 * MockMvc 를 Boot 의 자동 설정 어노테이션 없이 직접 만들어 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityAccessTests {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("비로그인: API 는 401")
    void anonymousApiIs401() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비로그인: 화면은 로그인 페이지로 이동")
    void anonymousPageRedirectsToLogin() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/mismatches")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("로그인/회원가입 페이지는 누구나 볼 수 있다")
    void loginAndSignupArePublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/signup")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER: 조회 API 는 허용")
    void userCanRead() throws Exception {
        mvc.perform(get("/api/jobs").with(user("u").roles("USER"))).andExpect(status().isOk());
        mvc.perform(get("/api/mismatches").with(user("u").roles("USER"))).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/summary").with(user("u").roles("USER"))).andExpect(status().isOk());
        mvc.perform(get("/api/uploads").with(user("u").roles("USER"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER: 업로드, Job 실행, 상태 변경은 403")
    void userCannotWrite() throws Exception {
        mvc.perform(post("/api/jobs").with(user("u").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/mismatches/1/status").with(user("u").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "a.csv", "text/csv", "x".getBytes()))
                        .param("type", "PAYMENT").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN: 권한은 통과하고, 잘못된 요청은 400 (403 이 아니다)")
    void adminPassesAuthorization() throws Exception {
        mvc.perform(post("/api/jobs").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/mismatches/999999/status").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("로그인한 사용자는 화면(Mustache 렌더링)을 볼 수 있다")
    void pagesRender() throws Exception {
        mvc.perform(get("/dashboard").with(user("u").roles("USER"))).andExpect(status().isOk());
        mvc.perform(get("/mismatches").with(user("u").roles("USER"))).andExpect(status().isOk());
    }
}
