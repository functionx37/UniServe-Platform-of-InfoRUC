package cn.edu.ruc.info.config;

import cn.edu.ruc.info.util.JwtUtil;
import cn.edu.ruc.info.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    // 构造器注入，确保 JwtUtil 可用
    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 OPTIONS 预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // --------------------------------------------------
        // 管理端接口：强制登录 + 仅允许角色 1 或 2
        // --------------------------------------------------
        if (path.startsWith("/admin/")) {
            String token = extractToken(request);
            if (token == null || !jwtUtil.validateToken(token)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"未登录或 token 无效\"}");
                return false;
            }

            Integer role = jwtUtil.getRoleFromToken(token);
            if (role == null || (role != 1 && role != 2)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"无权限访问\"}");
                return false;
            }

            // 将用户信息注入上下文
            UserContext.setUserId(jwtUtil.getUserIdFromToken(token));
            UserContext.setRoleId(role);
            return true;
        }

        // --------------------------------------------------
        // 非管理端接口：不强制，但尝试解析 token 方便后续使用
        // --------------------------------------------------
        String token = extractToken(request);
        if (token != null && jwtUtil.validateToken(token)) {
            UserContext.setUserId(jwtUtil.getUserIdFromToken(token));
            UserContext.setRoleId(jwtUtil.getRoleFromToken(token));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        // 请求结束，清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    /**
     * 从请求头中提取 Bearer token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}