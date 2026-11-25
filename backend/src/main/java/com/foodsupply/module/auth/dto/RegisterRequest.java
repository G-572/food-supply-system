package com.foodsupply.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 4, max = 20, message = "用户名长度需在 4-20 位之间")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "请输入密码")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,}$", message = "密码至少 6 位，且必须同时包含字母和数字")
    private String password;

    @NotBlank(message = "请输入姓名")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 只能是采购员或供货商，管理员不开放注册 */
    @NotBlank(message = "请选择注册身份")
    @Pattern(regexp = "^(PURCHASER|SUPPLIER)$", message = "注册身份只能是采购员或供货商")
    private String appliedRole;
}
