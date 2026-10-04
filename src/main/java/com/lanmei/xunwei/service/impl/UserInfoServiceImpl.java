package com.lanmei.xunwei.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.common.exception.BizException;
import com.lanmei.xunwei.entity.UserInfo;
import com.lanmei.xunwei.mapper.UserInfoMapper;
import com.lanmei.xunwei.service.IUserInfoService;
import com.lanmei.xunwei.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    /** 与 DDL 里 introduce varchar(128) 对齐 */
    private static final int INTRODUCE_MAX_LENGTH = 128;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> saveMyProfile(UserInfo profile) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        if (profile.getIntroduce() != null && profile.getIntroduce().length() > INTRODUCE_MAX_LENGTH) {
            // 在业务层挡掉，而不是等数据库处理：
            // 严格模式下 MySQL 直接报错，非严格模式下静默截断 ——
            // 后者用户完全看不出自己丢字了，是更难发现的问题。
            throw new BizException(ErrorCode.PARAM_ERROR, "个人介绍最多 " + INTRODUCE_MAX_LENGTH + " 个字");
        }

        // 用户 id 只能来自登录态，不能相信前端传上来的值：
        // 否则任何登录用户都能改别人的资料（水平越权）。
        profile.setUserId(userId);
        // 粉丝数 / 关注数 / 积分 / 等级由系统维护，不接受前端提交
        profile.setFans(null);
        profile.setFollowee(null);
        profile.setCredits(null);
        profile.setLevel(null);

        // upsert 语义：有就更新，没有就插入（前端不该关心这是第几次填）
        if (getById(userId) == null) {
            save(profile);
        } else {
            updateById(profile);
        }
        return Result.ok();
    }
}
