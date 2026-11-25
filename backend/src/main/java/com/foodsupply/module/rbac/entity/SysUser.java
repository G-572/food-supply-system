package com.foodsupply.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，任何接口都不允许返回 */
    private String password;

    private String realName;

    private String phone;

    /** PENDING / ACTIVE / DISABLED / REJECTED */
    private String status;

    /** 注册时申请的意向角色，审核通过时转正 */
    private String appliedRole;

    /** 供货商账号绑定的供货商 */
    private Long supplierId;

    /** 退出登录时 +1，令旧 Token 失效 */
    private Integer tokenVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
