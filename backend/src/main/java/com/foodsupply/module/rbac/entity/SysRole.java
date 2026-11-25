package com.foodsupply.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ADMIN / PURCHASER / SUPPLIER */
    private String roleCode;

    private String roleName;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
