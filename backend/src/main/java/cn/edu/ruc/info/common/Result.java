package cn.edu.ruc.info.common;

import lombok.Data;

@Data // 自动生成 Getter 和 Setter
public class Result<T> {
    private Boolean success;  // 是否成功
    private String message;   // 提示信息
    private T data;           // 具体的业务数据（比如用户信息、列表等）

    // 成功时的快捷调用
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setSuccess(true);
        r.setMessage("操作成功");
        r.setData(data);
        return r;
    }

    // 失败时的快捷调用
    public static <T> Result<T> error(String message) {
        Result<T> r = new Result<>();
        r.setSuccess(false);
        r.setMessage(message);
        r.setData(null);
        return r;
    }
}
