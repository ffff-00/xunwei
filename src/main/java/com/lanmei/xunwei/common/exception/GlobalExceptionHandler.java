package com.lanmei.xunwei.common.exception;

import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理。
 *
 * 两条重要的分界线
 *
 * ① 业务异常 vs 系统异常
 *    业务异常（BizException）是预期内的：库存不足、重复下单、参数不合法。
 *    这类打 warn 日志、返回对应业务 code，不打堆栈 —— 正常业务流转不该把日志刷满。
 *    系统异常是代码有 bug：打 error 日志 + 完整堆栈，返回 500，因为我们要能查。
 *
 * ② HTTP 状态码 vs 业务码
 *    程序性错误按 HTTP 语义返回（400/404/405/500），业务性失败返回 HTTP 200 + 业务 code。
 *    这样既保留 HTTP 的通用语义（监控、网关、浏览器缓存都认它），前端又能拿到细分的业务原因。
 *
 * 为什么原来那个 WebExceptionAdvice 不够
 *    它只 catch RuntimeException 并统一返回 "服务器异常"，于是：
 *    参数错、类型转换失败、资源不存在……全部变成 "服务器异常"，前端既没法提示用户，
 *    也没法区分"我传错了"和"服务挂了"。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：预期内，warn 级别，不打堆栈 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("业务异常 code={} message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(Result.fail(errorCode));
    }

    /** 参数缺失 / 类型不匹配：这类是调用方传错了，属于 400，不是 500 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        log.warn("请求参数不合法：{}", e.getMessage());
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.PARAM_ERROR, e.getMessage()));
    }

    /** 兜底：真正的系统异常。这里必须打完整堆栈，否则线上无从排查 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception e) {
        log.error("未预期的系统异常", e);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYSTEM_ERROR));
    }
}
