package com.lanmei.xunwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.entity.UserInfo;

/**
 * 用户详情服务（tb_user_info）。
 * 认证字段在 tb_user、资料字段在这里：两者变更频率完全不同，分表和分接口更清楚。
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 保存当前登录用户的资料。
     * 没有资料行时创建、已有则更新 —— 前端不该关心"这是第一次填还是第 N 次改"，
     * 所以对调用方只暴露一个 upsert 语义的接口。
     */
    Result<Void> saveMyProfile(UserInfo profile);
}
