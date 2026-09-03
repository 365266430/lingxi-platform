package com.lingxi.modules.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标时序数据。
 */
@Data
@TableName("ops_metric")
public class OpsMetric {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String serviceName;

    /** cpu_usage | mem_usage */
    private String metricName;

    private Double metricValue;

    private LocalDateTime collectedAt;
}
