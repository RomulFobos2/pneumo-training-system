package ru.mai.voshod.pneumotraining.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class AuthenticationLoggingSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        String roleNames = authentication.getAuthorities().toString();
        String remoteAddress = request.getRemoteAddr();

        log.info("Успешная авторизация сотрудника: username={}, roles={}, remoteAddress={}",
                username, roleNames, remoteAddress);

        if (request.getSession(false) != null) {
            log.debug("Сессия после успешной авторизации: username={}, sessionId={}",
                    username, request.getSession(false).getId());
        }

        response.sendRedirect("/");
    }
}
