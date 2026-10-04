package com.lanmei.xunwei.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @since 2021-12-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，用户id。
     *
     * 必须是 INPUT 而不是 AUTO：这一列是外键（指向 tb_user.id），数据库里没有自增。
     * 原来写成 IdType.AUTO，MyBatis-Plus 插入时会省略这一列、指望数据库生成 ——
     * 而表里没有 AUTO_INCREMENT，会直接报 Field 'user_id' doesn't have a default value。
     * 表现是"编辑资料第一次保存就失败"，且只对还没有资料行的用户出现，很难查。
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /**
     * 城市名称
     */
    private String city;

    /**
     * 个人介绍，不要超过128个字符
     */
    private String introduce;

    /**
     * 粉丝数量
     */
    private Integer fans;

    /**
     * 关注的人的数量
     */
    private Integer followee;

    /**
     * 性别，0：男，1：女
     */
    private Boolean gender;

    /**
     * 生日
     */
    private LocalDate birthday;

    /**
     * 积分
     */
    private Integer credits;

    /**
     * 会员级别，0~9级,0代表未开通会员
     */
    private Boolean level;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
