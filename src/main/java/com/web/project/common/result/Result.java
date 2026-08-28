package com.web.project.common.result;

/**
 * 后端统一返回结果类
 *
 * @param code 业务状态码，例如：200成功，500失败
 * @param msg  提示信息，失败时通常返回具体错误原因
 * @param data 返回给前端的数据
 * @param <T>  data 的数据类型
 */
public record Result<T>(
        int code,
        String msg,
        T data
) {

    /**
     * 请求成功，没有返回数据
     *
     * Void 表示这个 Result 不携带具体数据
     */
    public static Result<Void> success() {

        return new Result<>(200, null, null);
    }

    /**
     * 请求成功，并返回数据
     *
     * <T> 表示这是一个泛型方法，
     * 传入什么类型的数据，返回的 Result 就是什么类型
     */
    public static <T> Result<T> success(T data) {

        return new Result<>(200, null, data);
    }

    /**
     * 请求失败，并允许自定义状态码
     */
    public static <T> Result<T> error(int code, String msg) {

        return new Result<>(code, msg, null);
    }
}
