package com._project4.project4.common;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiAuditLoggingFilter extends OncePerRequestFilter {

    static final String ATTR_BUSINESS_CODE = "api.audit.businessCode";

    private static final Logger log = LoggerFactory.getLogger(ApiAuditLoggingFilter.class);
    private static final Pattern ROBOT_PATH_PATTERN = Pattern.compile(
            "^/api/v1/robots(?:/([^/]+)(?:/(heartbeat|status|commands)(?:/([^/]+))?)?)?/?$"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = System.currentTimeMillis() - start;
            String requestUri = request.getRequestURI();
            String robotId = "-";
            String commandId = "-";

            Matcher matcher = ROBOT_PATH_PATTERN.matcher(requestUri);
            if (matcher.matches()) {
                if (matcher.group(1) != null) {
                    robotId = matcher.group(1);
                }
                if (matcher.group(3) != null) {
                    commandId = matcher.group(3);
                }
            }

            Object businessCode = request.getAttribute(ATTR_BUSINESS_CODE);
            int code = businessCode instanceof Integer ? (Integer) businessCode : response.getStatus();

            log.info(
                    "api_audit method={} path={} httpStatus={} code={} robotId={} commandId={} costMs={}",
                    request.getMethod(),
                    requestUri,
                    response.getStatus(),
                    code,
                    robotId,
                    commandId,
                    costMs
            );
        }
    }
}