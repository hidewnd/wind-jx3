package com.hidewnd.winds.scout.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 校验微博接口 Token，账号池操作额外校验管理权限。
 */
@Component
@Slf4j
public class ScoutManagementInterceptor implements HandlerInterceptor {

    public static final String TOKEN_ATTRIBUTE = "scoutToken";

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
        ScoutAuthorization authorization;
        try {
            authorization = authenticator.authorize(token);
        } catch (RuntimeException exception) {
            log.error("微博接口鉴权失败，异常类型={}", exception.getClass().getSimpleName());
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "鉴权服务暂不可用");
            return false;
        }
        boolean managementOnly = handler instanceof HandlerMethod method
                && method.hasMethodAnnotation(WeiboAccountManagement.class);
        if (authorization == ScoutAuthorization.AUTHORIZED
                || authorization == ScoutAuthorization.FORBIDDEN && !managementOnly) {
            request.setAttribute(TOKEN_ATTRIBUTE, token);
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
