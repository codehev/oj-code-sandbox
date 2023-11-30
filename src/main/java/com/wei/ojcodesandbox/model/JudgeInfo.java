package com.wei.ojcodesandbox.model;

import lombok.Data;

/**
 * 判题信息
 * dto，接收前端数据，业务之间传递数据
 */
@Data
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;
    /**
     * 消耗内存kb
     */
    private Long memory;
    /**
     * 耗时ms
     */
    private Long time;
}
