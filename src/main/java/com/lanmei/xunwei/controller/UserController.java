package com.lanmei.xunwei.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.dto.LoginFormDTO;
import com.lanmei.xunwei.dto.UserDTO;
import com.lanmei.xunwei.entity.User;
import com.lanmei.xunwei.entity.UserInfo;
import com.lanmei.xunwei.service.IUserInfoService;
import com.lanmei.xunwei.service.IUserService;
import com.lanmei.xunwei.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：验证码 / 登录 / 登出 / 资料 / 签到。
 *
 * 令牌怎么传
 * 登录成功后在响应体 `data` 里返回令牌，之后每次请求放到 `authorization` 请求头里。
 * （原项目早期版本是放在响应头里返回的，需要前端专门去读 header；放 body 里少一层约定。）
 * 需要登录的路径由拦截器统一校验，见 config/MvcConfig。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    /** 登录令牌所在的请求头 */
    private static final String TOKEN_HEADER = "authorization";

    private final IUserService userService;
    private final IUserInfoService userInfoService;

    public UserController(IUserService userService, IUserInfoService userInfoService) {
        this.userService = userService;
        this.userInfoService = userInfoService;
    }

    /** 发送手机验证码（验证码在服务端日志里，本地调试用） */
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /** 手机号 + 验证码登录，成功返回令牌 */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 登出：把 Redis 里的登录态删掉。
     * 原项目里这是个 `TODO 实现登出功能` 的空桩，直接返回"功能未完成"——
     * 前端点了登出其实没登出，这个洞补上。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return userService.logout(token);
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public Result<UserDTO> me() {
        return Result.ok(UserHolder.getUser());
    }

    /** 用户详情（无详情时返回空 data，前端按"未填写"处理，不算错误） */
    @GetMapping("/info/{id}")
    public Result<UserInfo> info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        // 这两个是内部审计字段，不该给前端
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    /**
     * 保存我的资料。
     * 路径用 /info 而不是 /info/{id}：改的只能是自己，
     * 用户 id 从登录态取（见 UserInfoServiceImpl），带 id 反而给了越权的口子。
     */
    @PutMapping("/info")
    public Result<Void> saveMyProfile(@RequestBody UserInfo profile) {
        return userInfoService.saveMyProfile(profile);
    }

    /** 按 id 查用户基本信息 */
    @GetMapping("/{id}")
    public Result<UserDTO> queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    /** 签到（Bitmap） */
    @PostMapping("/sign")
    public Result<Void> sign() {
        return userService.sign();
    }

    /** 连续签到天数 */
    @GetMapping("/sign/count")
    public Result<Integer> signCount() {
        return userService.signCount();
    }
}
