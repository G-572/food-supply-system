package com.foodsupply.module.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;

    /** 有效期，单位秒，前端可据此提前续期 */
    private long expiresIn;

    private UserInfoVO user;
}
