package com.coding.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private String password;
    /**
     * 用户等级：0-禁用 1-普通用户 2-升级版 3-专业版
     */
    private Integer status;
    private String email;
    private String image;
}
