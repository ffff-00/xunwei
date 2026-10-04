package com.lanmei.xunwei.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * 统一响应结构。
 *
 * 相比原来的 `{success, errorMsg, data, total}`，核心改动是换成业务错误码：
 * `success=false` 只能说明"失败了"，前端要区分"库存不足"和"已经买过"就得去匹配 errorMsg 字符串，
 * 改一个字就失效；`code` 是稳定的契约，前端可以按码分支（44004 提示已购买、44005 继续轮询）。
 *
 * `total` 放在顶层是为了兼容分页：列表放 data，总数放 total。
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    /** 分页总数，非分页接口为 null（Jackson 配置了 non_null，不会出现在响应里） */
    private Long total;

    private Result(int code, String message, T data, Long total) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.total = total;
    }

    // 成功

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), null, null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), null, data, null);
    }

    /** 分页成功：列表 + 总数 */
    public static <T> Result<List<T>> ok(List<T> data, Long total) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), null, data, total);
    }

    // 失败

    /**
     * 带业务错误码的失败。新代码优先用这个，前端才能按码做分支。
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    /** 带业务错误码 + 补充说明（说明只用于排查，前端判断仍应基于 code） */
    public static <T> Result<T> fail(ErrorCode errorCode, String detail) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage() + ": " + detail, null, null);
    }

    /**
     * 只有一句话、暂时还没归类的失败。
     * 保留它是为了让既有调用点不用一次性全改；新代码不要再用它（见技术债）。
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(ErrorCode.SYSTEM_ERROR.getCode(), message, null, null);
    }

    /**
     * 供 Java 代码判断用的便捷方法。
     *
     * 必须标 @JsonIgnore：Jackson 会把 isXxx() 当成一个名为 xxx 的属性序列化出去，
     * 不标的话响应里会多出一个 success 字段（实测踩过）。而且这个字段和 code 表达的是同一件事，
     * 两处真相迟早不一致——干脆不输出。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}
