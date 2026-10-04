package com.lanmei.xunwei.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.dto.LoginFormDTO;
import com.lanmei.xunwei.dto.UserDTO;
import com.lanmei.xunwei.entity.User;
import com.lanmei.xunwei.mapper.UserMapper;
import com.lanmei.xunwei.service.IUserService;
import com.lanmei.xunwei.utils.RegexUtils;
import com.lanmei.xunwei.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.lanmei.xunwei.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.lanmei.xunwei.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.lanmei.xunwei.utils.RedisConstants.LOGIN_USER_KEY;
import static com.lanmei.xunwei.utils.RedisConstants.LOGIN_USER_TTL;
import static com.lanmei.xunwei.utils.RedisConstants.USER_SIGN_KEY;
import static com.lanmei.xunwei.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 用户服务实现。
 *
 * 登录态为什么放 Redis 而不是 Session
 * 令牌方案（token → Redis Hash → UserDTO）让服务端完全无状态：
 *   - 重启不掉线、多实例不用做 session 共享
 *   - 令牌可以被服务端主动吊销（登出、封号），这是纯 JWT 做不到的
 * 代价是每次请求多一次 Redis 查询 —— 用"拦截器里查一次、放进 ThreadLocal 供后续复用"抵消掉。
 *
 * 签到为什么用 Bitmap
 * 一个用户一个月最多 31 天，用 String 存"哪天签到了"要 31 个字符或一个 JSON；
 * Bitmap 把它压成 4 个字节，而且"本月签到几天"这种统计可以直接用 BITFIELD 一次取出。
 * 这是"用对数据结构"的典型例子，不是炫技。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final DateTimeFormatter SIGN_KEY_MONTH = DateTimeFormatter.ofPattern(":yyyyMM");

    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result<Void> sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(ErrorCode.PHONE_FORMAT_ERROR);
        }

        // 6 位数字验证码。注意这里是 randomNumbers 不是 randomString ——
        // randomString 会生成字母，和"短信验证码"的约定不符，前端按纯数字处理时会直接失败。
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 真实项目这里要接短信网关。本项目不签约第三方，验证码只打日志（本地调试直接从日志里拿）。
        // 注意：真要上线，这行日志本身就是安全漏洞（验证码进日志），必须去掉。
        log.info("发送短信验证码成功 phone={} 验证码={}", phone, code);
        return Result.ok();
    }

    @Override
    public Result<String> login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(ErrorCode.PHONE_FORMAT_ERROR);
        }

        String cachedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String inputCode = loginForm.getCode();
        if (cachedCode == null || !cachedCode.equals(inputCode)) {
            // 验证码错误和验证码过期合并成同一个码返回：分开提示等于告诉攻击者"这个手机号收过码"
            return Result.fail(ErrorCode.LOGIN_CODE_ERROR);
        }

        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 令牌写进 Redis Hash，同时设过期时间（续期由 RefreshTokenInterceptor 负责）
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 验证码用过即失效：不清掉的话，同一个码在有效期内可以反复登录
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        return Result.ok(token);
    }

    @Override
    public Result<Void> logout(String token) {
        if (StringUtils.hasText(token)) {
            // 删掉 Redis 里的登录态，令牌立刻作废（这就是"服务端可主动吊销"的具体实现）
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    @Override
    public Result<Void> sign() {
        String key = signKey();
        int dayOfMonth = LocalDateTime.now().getDayOfMonth();
        // offset 从 0 开始，所以第 1 天写在 bit 0
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result<Integer> signCount() {
        String key = signKey();
        int dayOfMonth = LocalDateTime.now().getDayOfMonth();

        // 一次取出本月 1 号到今天的所有位（BITFIELD key GET u{dayOfMonth} 0），拿到一个十进制数，
        // 它的二进制低位到高位正好对应"从今天往前数"的签到情况
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty() || result.get(0) == null || result.get(0) == 0) {
            return Result.ok(0);
        }

        // 从最低位开始数连续的 1，遇到第一个 0 就停 —— 也就是"连续签到天数"
        long num = result.get(0);
        int count = 0;
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private String signKey() {
        Long userId = UserHolder.getUser().getId();
        return USER_SIGN_KEY + userId + LocalDateTime.now().format(SIGN_KEY_MONTH);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
