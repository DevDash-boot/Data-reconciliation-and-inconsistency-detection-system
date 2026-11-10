package com.example.recon.auth;

import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.ConflictException;
import com.example.recon.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 로그인/회원가입 화면. 로그인 처리(POST /login)와 로그아웃(POST /logout)은 Spring Security 가 한다.
 * 템플릿에 CSRF 토큰을 넘기고, 모든 값은 null 없이 채워서 넘긴다. (JMustache 는 null 에서 예외가 날 수 있다)
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String registered,
                            CsrfToken csrf, Model model) {
        model.addAttribute("csrf", csrf);
        model.addAttribute("error", error != null);
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("registered", registered != null);
        return "login";
    }

    @GetMapping("/signup")
    public String signupForm(CsrfToken csrf, Model model) {
        fillSignup(model, csrf, "", "");
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam String username, @RequestParam String password,
                         CsrfToken csrf, Model model) {
        try {
            userService.register(username, password);
            return "redirect:/login?registered";
        } catch (BadRequestException | ConflictException e) {
            fillSignup(model, csrf, username, e.getMessage());
            return "signup";
        }
    }

    private void fillSignup(Model model, CsrfToken csrf, String username, String errorMessage) {
        model.addAttribute("csrf", csrf);
        model.addAttribute("username", username);
        model.addAttribute("hasError", !errorMessage.isEmpty());
        model.addAttribute("errorMessage", errorMessage);
    }
}
