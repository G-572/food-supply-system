package com.foodsupply.module.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoVO {

    private Long id;

    private String username;

    private String realName;

    private String status;

    private Long supplierId;

    private List<String> roles;

    private List<String> permissions;
}
