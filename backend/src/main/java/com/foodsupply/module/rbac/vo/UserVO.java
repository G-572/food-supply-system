package com.foodsupply.module.rbac.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户展示对象。实体里的 password、token_version 一律不出现在这里。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String status;

    /** 注册时申请的意向角色，审核时才参考 */
    private String appliedRole;

    private Long supplierId;

    private List<String> roles;

    private LocalDateTime createdAt;
}
