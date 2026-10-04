package com.lanmei.xunwei.common.exception;

import com.lanmei.xunwei.common.api.ErrorCode;

/**
 * 业务异常：抛出即代表"这个操作不该发生，且原因可预期（是调用方的问题或业务规则不允许）"。
 *
 * 与"系统异常"的区别很重要：业务异常由全局处理器翻译成对应的业务 code + HTTP 状态，
 * 是预期内的正常返回路径；系统异常进 error 日志，返回 500，因为它代表代码有 bug。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
