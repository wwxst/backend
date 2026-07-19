package com.web.project.common.exception;

import com.web.project.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 项目中的 Controller 或 Service 抛出异常后，
 * 会由这个类统一转换成 Result 返回给前端。
 * 从代码规范和维护性角度，建议按 "具体异常在前，通用兜底在后" 的原则排序
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数校验异常。
     *
     * 例如：
     * 账号为空、密码长度不足等情况。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        // 获取第一个字段校验错误
        FieldError fieldError = exception
                .getBindingResult()
                .getFieldError();

        // 正常情况下会有错误信息，这里增加空值判断作为保护
        String message = fieldError == null
                ? "请求参数不正确"
                : fieldError.getDefaultMessage();

        return Result.error(400, message);
    }

    /**
     * 处理自定义业务异常。
     * <p>
     * 例如：用户不存在、密码错误、账号被禁用。
     *
     * @param exception 捕获到的业务异常
     * @return 统一错误结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.error(
                exception.getCode(),
                exception.getMessage()
        );
    }

    /**
     * 处理没有被单独处理的系统异常。
     *
     * Exception 是大多数普通异常的父类，
     * 所以这个方法相当于最后一道保护。
     *
     * @param exception 捕获到的系统异常
     * @return 统一错误结果
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        // 将完整异常记录到后端日志中，方便开发人员排查。
        // 不要把数据库、代码路径等详细报错直接返回给前端。
        log.error("服务器内部异常", exception);

        return Result.error(500, "服务器内部异常");
    }

}
