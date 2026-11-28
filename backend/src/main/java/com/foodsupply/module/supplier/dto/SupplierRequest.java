package com.foodsupply.module.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增与修改共用。状态不在这里改，走单独的 /status 接口，
 * 免得改个电话顺手把供货商停用了。
 */
@Data
public class SupplierRequest {

    @NotBlank(message = "请输入供货商名称")
    @Size(max = 100, message = "名称不能超过 100 个字")
    private String name;

    @Size(max = 50, message = "联系人不能超过 50 个字")
    private String contactPerson;

    /** 供货商留的可能是座机，这里只限长度，不卡手机号格式 */
    @Size(max = 20, message = "联系电话不能超过 20 位")
    private String phone;

    @Size(max = 255, message = "地址不能超过 255 个字")
    private String address;

    @Size(max = 255, message = "备注不能超过 255 个字")
    private String remark;
}
