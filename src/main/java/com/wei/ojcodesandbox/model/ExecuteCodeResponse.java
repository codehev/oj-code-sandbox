package com.wei.ojcodesandbox.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder//构造器的方式创建对象
@NoArgsConstructor//无参构造
@AllArgsConstructor//有参构造
public class ExecuteCodeResponse {
    private List<String> outputList;
    /**
     * 接口信息，执行信息
     */
    private String message;
    /**
     * 执行状态
     */
    private Integer status;

    private JudgeInfo judgeInfo;
}
