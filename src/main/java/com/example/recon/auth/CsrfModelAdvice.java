package com.example.recon.auth;

import com.example.recon.dashboard.DashboardViewController;
import com.example.recon.mismatch.MismatchViewController;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 화면(헤더의 로그아웃 폼)에서 쓸 CSRF 토큰을 모든 화면 모델에 넣는다. */
@ControllerAdvice(assignableTypes = {MismatchViewController.class, DashboardViewController.class})
public class CsrfModelAdvice {

    @ModelAttribute("csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }
}
