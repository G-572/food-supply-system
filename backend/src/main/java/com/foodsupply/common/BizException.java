package com.foodsupply.common;

import lombok.Getter;

/**
 * 业务异常。抛出来后由全局异常处理器翻译成统一格式，
 * 不把 Java 堆栈信息泄露给前端。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(ResultCode.UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }
}
