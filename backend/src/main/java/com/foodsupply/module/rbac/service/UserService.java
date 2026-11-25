package com.foodsupply.module.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodsupply.common.PageResult;
import com.foodsupply.module.rbac.entity.SysUser;
import com.foodsupply.module.rbac.mapper.SysUserMapper;
import com.foodsupply.module.rbac.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper userMapper;

    /**
     * 用户列表。keyword 同时匹配登录名和姓名，status 可选。
     * 角色先按单条查，等数据量上来再改成一次批量取（TODO）。
     */
    public PageResult<UserVO> page(int pageNum, int pageSize, String keyword, String status) {
        int current = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(StringUtils.hasText(status), SysUser::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getRealName, keyword))
                .orderByDesc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page, this::toVO);
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setAppliedRole(user.getAppliedRole());
        vo.setSupplierId(user.getSupplierId());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setRoles(userMapper.selectRoleCodes(user.getId()));
        return vo;
    }
}
