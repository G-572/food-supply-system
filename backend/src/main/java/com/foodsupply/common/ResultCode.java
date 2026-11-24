package com.foodsupply.common;

import lombok.Getter;

/**
 * 业务状态码，前三位跟 HTTP 状态码对齐，方便前端拦截器统一处理。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "当前状态不允许该操作"),
    INTERNAL_ERROR(500, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
