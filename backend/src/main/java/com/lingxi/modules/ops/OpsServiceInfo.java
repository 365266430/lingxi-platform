package com.lingxi.modules.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CMDB 服务。
 */
@Data
@TableName("ops_service_info")
public class OpsServiceInfo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String serviceName;

    private String owner;

    /** 核心等级 1~3 */
    private Integer tier;

    /** 部署主机名，逗号分隔 */
    private String hostNames;

    private String repo;

    private LocalDateTime createdAt;
}
