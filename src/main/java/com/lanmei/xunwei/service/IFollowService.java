package com.lanmei.xunwei.service;

import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.entity.Follow;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
