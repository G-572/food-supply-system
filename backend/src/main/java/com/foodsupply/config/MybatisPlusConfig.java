package com.foodsupply.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.foodsupply.module.**.mapper")
public class MybatisPlusConfig {
    // 分页插件暂时不加，等到列表接口需要分页时再引 mybatis-plus-jsqlparser
}
