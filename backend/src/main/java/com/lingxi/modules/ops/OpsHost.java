package com.lingxi.modules.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CMDB 主机。
 */
@Data
@TableName("ops_host")
public class OpsHost {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String hostName;

    private String ip;

    private Integer cpuCores;

    private Integer memoryGb;

    private Integer diskGb;

    /** PROD | STAGING | DEV */
    private String env;

    /** ONLINE | OFFLINE */
    private String status;

    private LocalDateTime createdAt;
}
