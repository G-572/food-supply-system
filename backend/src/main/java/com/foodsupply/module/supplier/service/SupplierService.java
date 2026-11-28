package com.foodsupply.module.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodsupply.common.BizException;
import com.foodsupply.common.PageResult;
import com.foodsupply.module.supplier.dto.SupplierRequest;
import com.foodsupply.module.supplier.dto.SupplierStatusRequest;
import com.foodsupply.module.supplier.entity.Supplier;
import com.foodsupply.module.supplier.mapper.SupplierMapper;
import com.foodsupply.module.supplier.vo.SupplierVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SupplierMapper supplierMapper;

    /** keyword 同时匹配名称、联系人和电话，采购员建计划时多半按名字找 */
    public PageResult<SupplierVO> page(int pageNum, int pageSize, String keyword, String status) {
        int current = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Supplier> wrapper = Wrappers.<Supplier>lambdaQuery()
                .eq(StringUtils.hasText(status), Supplier::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Supplier::getName, keyword)
                        .or()
                        .like(Supplier::getContactPerson, keyword)
                        .or()
                        .like(Supplier::getPhone, keyword))
                .orderByAsc(Supplier::getId);

        Page<Supplier> page = supplierMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page, this::toVO);
    }

    public SupplierVO get(Long id) {
        return toVO(requireSupplier(id));
    }

    @Transactional
    public Long create(SupplierRequest request) {
        assertNameAvailable(request.getName(), null);

        Supplier supplier = new Supplier();
        supplier.setName(request.getName().trim());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setRemark(request.getRemark());
        supplier.setStatus(STATUS_ACTIVE);
        supplierMapper.insert(supplier);

        log.info("新增供货商: id={}, name={}", supplier.getId(), supplier.getName());
        return supplier.getId();
    }

    @Transactional
    public void update(Long id, SupplierRequest request) {
        Supplier supplier = requireSupplier(id);
        assertNameAvailable(request.getName(), id);

        supplier.setName(request.getName().trim());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setRemark(request.getRemark());
        supplierMapper.updateById(supplier);
    }

    @Transactional
    public void changeStatus(Long id, SupplierStatusRequest request) {
        requireSupplier(id);
        supplierMapper.update(null, Wrappers.<Supplier>lambdaUpdate()
                .eq(Supplier::getId, id)
                .set(Supplier::getStatus, request.getStatus()));
        log.info("供货商状态变更: id={}, status={}", id, request.getStatus());
    }

    /**
     * 有商品或账号挂着就不给删。外键会直接报错，但错误信息对前端没意义，
     * 这里先查一遍给出人话。
     */
    @Transactional
    public void delete(Long id) {
        requireSupplier(id);

        long products = supplierMapper.countProducts(id);
        if (products > 0) {
            throw BizException.conflict("该供货商下还有 " + products + " 个商品，请先清理商品");
        }
        long users = supplierMapper.countUsers(id);
        if (users > 0) {
            throw BizException.conflict("还有 " + users + " 个账号绑定这家供货商，请先解绑");
        }

        supplierMapper.deleteById(id);
        log.info("删除供货商: id={}", id);
    }

    private Supplier requireSupplier(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw BizException.notFound("供货商不存在");
        }
        return supplier;
    }

    /** excludeId 用于修改时把自己排除在重名校验外 */
    private void assertNameAvailable(String name, Long excludeId) {
        Long count = supplierMapper.selectCount(Wrappers.<Supplier>lambdaQuery()
                .eq(Supplier::getName, name.trim())
                .ne(excludeId != null, Supplier::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("供货商名称已存在");
        }
    }

    private SupplierVO toVO(Supplier supplier) {
        SupplierVO vo = new SupplierVO();
        vo.setId(supplier.getId());
        vo.setName(supplier.getName());
        vo.setContactPerson(supplier.getContactPerson());
        vo.setPhone(supplier.getPhone());
        vo.setAddress(supplier.getAddress());
        vo.setStatus(supplier.getStatus());
        vo.setRemark(supplier.getRemark());
        vo.setCreatedAt(supplier.getCreatedAt());
        return vo;
    }
}
