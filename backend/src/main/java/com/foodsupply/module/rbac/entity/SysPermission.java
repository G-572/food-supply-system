package com.foodsupply.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作级权限码，如 order:confirm */
    private String permCode;

    private String permName;

    /** 所属模块，用于前端分组展示 */
    private String module;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
