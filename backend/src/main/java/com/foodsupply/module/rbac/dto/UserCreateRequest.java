package com.foodsupply.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理员手工建号。与注册接口的区别：这里直接落 ACTIVE，并要求指定角色。
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 4, max = 20, message = "用户名长度需在 4-20 位之间")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "请输入初始密码")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,}$", message = "密码至少 6 位，且必须同时包含字母和数字")
    private String password;

    @NotBlank(message = "请输入姓名")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 只能是 PURCHASER 或 SUPPLIER，管理员不通过接口创建 */
    @NotEmpty(message = "请至少选择一个角色")
    private List<String> roleCodes;

    /** 供货商账号必须绑定供货商 */
    private Long supplierId;
}
