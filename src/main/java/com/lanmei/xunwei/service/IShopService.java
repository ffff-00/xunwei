package com.lanmei.xunwei.service;

import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.entity.Shop;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
