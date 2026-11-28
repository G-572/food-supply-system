package com.foodsupply.module.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供货商。收货商只有一家，按需求书不建表。
 */
@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全表唯一，建库时有 uk_supplier_name 兜底 */
    private String name;

    private String contactPerson;

    private String phone;

    private String address;

    /** ACTIVE 合作中 / DISABLED 停用 */
    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
