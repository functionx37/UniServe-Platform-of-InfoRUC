package cn.edu.ruc.info.util;

/**
 * 用户上下文：基于 ThreadLocal 存储当前请求的用户ID和角色ID，
 * 在拦截器 preHandle 中设置，afterCompletion 中清除。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setRoleId(Integer roleId) {
        ROLE_ID.set(roleId);
    }

    public static Integer getRoleId() {
        return ROLE_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLE_ID.remove();
    }
}