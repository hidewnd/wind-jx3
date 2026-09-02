package com.hidewnd.winds.scout.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Scout 管理接口鉴权拦截器，校验 Bearer Token 并返回统一的 401 或 403 响应。
 */
@Component
public class ScoutManagementInterceptor implements HandlerInterceptor {

    private final ScoutManagementTokenAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public ScoutManagementInterceptor(
            ScoutManagementTokenAuthenticator authenticator,
            ObjectMapper objectMapper) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring("Bearer ".length()).trim()
                : null;
        ScoutAuthorization authorization = authenticator.authorize(token);
        if (authorization == ScoutAuthorization.AUTHORIZED) {
            return true;
        }
        if (authorization == ScoutAuthorization.FORBIDDEN) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "权限不足");
        } else {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "未授权");
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success", false,
                "code", status,
                "msg", message));
    }
}
