package com.lingxi.modules.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警。
 */
@Data
@TableName("ops_alert")
public class OpsAlert {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    /** P1 | P2 | P3 */
    private String severity;

    private String serviceName;

    private String hostName;

    private String metricName;

    private String metricValue;

    /** ACTIVE | RESOLVED */
    private String status;

    private String description;

    private LocalDateTime startedAt;

    private LocalDateTime resolvedAt;
}
