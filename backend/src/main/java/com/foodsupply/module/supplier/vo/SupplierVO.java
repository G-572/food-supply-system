package com.foodsupply.module.supplier.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierVO {

    private Long id;

    private String name;

    private String contactPerson;

    private String phone;

    private String address;

    private String status;

    private String remark;

    private LocalDateTime createdAt;
}
