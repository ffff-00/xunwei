package com.lanmei.xunwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.dto.LoginFormDTO;
import com.lanmei.xunwei.entity.User;

/**
 * 用户服务。
 *
 * 登录态不依赖 HttpSession：验证码和登录令牌都放在 Redis 里，
 * 服务端完全无状态，重启与多实例都不影响已登录用户。
 * （原项目早期版本用 session，改成 Redis 后 HttpSession 参数一直没删，是残留代码，这里清掉。）
 */
public interface IUserService extends IService<User> {

    /** 发送手机验证码。验证码写进 Redis 并设过期时间 */
    Result<Void> sendCode(String phone);

    /** 手机号 + 验证码登录。成功返回登录令牌（放在响应体的 data 里） */
    Result<String> login(LoginFormDTO loginForm);

    /** 登出：删除 Redis 中的登录态，令牌立刻失效 */
    Result<Void> logout(String token);

    /** 用户签到（用 Bitmap 记录当月每天是否签到） */
    Result<Void> sign();

    /** 连续签到天数（从今天往前数，遇到第一个未签到就停） */
    Result<Integer> signCount();
}
