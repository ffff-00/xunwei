package com.lanmei.xunwei.dto;

import lombok.Data;

@Data
public class UserDTO {   //返回给前端的数据,因为要部分返回,避免返回敏感信息
    private Long id;
    private String nickName;
    private String icon;
}
