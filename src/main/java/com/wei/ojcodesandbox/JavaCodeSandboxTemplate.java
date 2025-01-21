package com.wei.ojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wei.ojcodesandbox.model.ExecuteCodeRequest;
import com.wei.ojcodesandbox.model.ExecuteCodeResponse;
import com.wei.ojcodesandbox.model.ExecuteMessage;
import com.wei.ojcodesandbox.model.JudgeInfo;
import com.wei.ojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模版方法
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    /**
     * java文件名为Main.java
     * <p>
     * java公共（public）类名要与文件名同名，而又运行class文件需要指定文件名（java -cp ./ Main）
     * 所以用户输入代码的类名限制为 Main（参考 Poj）,可以减少编译时类名不一致的风险,而且不用从用户代码中提取类名，更方便。
     */
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //设置最大运行时间毫秒，超过则结束进程
    private static final long TIME_OUT = 5000L;

    //SecurityManager类的.class文件路径
    private static final String SECURITY_MANAGER_PATH = "C:\\code\\oj-code-sandbox\\src\\main\\resources\\security";
    //SecurityManager类的.class文件名字
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        /**
         * 1.把用户的代码保存为文件
         */
        File userCodeFile = saveCodeToFile(code);
        /**
         * 2.编译代码，得到 class 文件
         */
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
//        System.out.println("compileFileExecuteMessage：" + compileFileExecuteMessage);

        /**
         * 3.执行代码，得到输出结果
         *
         * 注意：有多个输入用例，会运行多次
         */
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        /**
         * 4.收集整理输出结果
         */
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        /**
         * 5.文件清理，释放空间
         */
        boolean b = deleteFile(userCodeFile);
        if (!b) {
            log.error("删除文件失败，文件路径为：{}", userCodeFile.getParentFile().getAbsolutePath());
        }

        return executeCodeResponse;
    }

    /**
     * 1.把用户的代码保存为文件
     *
     * @return code 用户代码文件
     */
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码隔离存放
        //存放代码目录路径
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        //代码路径
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //代码文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2.编译代码，得到 class 文件
     */
    public ExecuteMessage compileFile(File userCodeFile) {

        //控制台编码与java文件编码可能不一致，指定编译编码-encoding utf-8，解决编译乱码（输出结果中文乱码）
        //%s类似于C语言的printf格式化输出
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            //执行命令行命令
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
//            return getErrorResponse(e);
        }
    }


    /**
     * 3.执行代码，得到输出结果
     * <p>
     * 注意：有多个输入用例，会运行多次
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //Xmx512m（最大堆空间大小,不能太小，可能运行程序会报错）-Xms（初始堆空间大小），防止运行的代码无限占用空间
            //注意！-Xmx参数。JVM的堆内存限制，不等同于系统实际占用的最大资源，可能会超出。
            //-Dfile.encoding=UTF-8解决运行乱码（输出结果中文乱码）
            //第一个%s目录路径，第二个%s输入参数列表（java代码 main函数的参数String[] args）
            //;%s -Djava.security.manager=%s 第一个%s安全管理器的编译好的.class文件路径，第二个%s.class文件名
            String runCmd = String.format("java -Xmx512m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
//            String runCmd = String.format("java -Xmx512m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                //执行命令行命令
                Process runProcess = Runtime.getRuntime().exec(runCmd);

                // 超时控制，在创建一个守护线程，超过限制时间，销毁runProcess
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                //ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
                executeMessageList.add(executeMessage);
//                System.out.println(executeMessage + "\n");
            } catch (Exception e) {
                throw new RuntimeException("程序执行异常", e);
//                return getErrorResponse(e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4.收集整理输出结果
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {

        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        //取用时最大值，便于判断是否超时
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            //错误信息非空
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                //执行过程中存在错误，3代表失败
                executeCodeResponse.setStatus(3);
                break;
            }
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            Long memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
            //执行过程中不存在错误
            outputList.add(executeMessage.getMessage());
        }
        if (outputList.size() == executeMessageList.size()) {
            // 2代表成功
            executeCodeResponse.setStatus(2);
            executeCodeResponse.setOutputList(outputList);

            JudgeInfo judgeInfo = new JudgeInfo();
            //judgeInfo.setMessage();//非程序执行结果输出的信息,在判题服务实例设置，此处不设置
            judgeInfo.setMemory(maxMemory);//比较难实现获取内存,单位kb
            judgeInfo.setTime(maxTime);//单位毫秒ms

            executeCodeResponse.setJudgeInfo(judgeInfo);
        } else {
            executeCodeResponse.setStatus(3);
        }

        return executeCodeResponse;
    }

    /**
     * 5.文件清理，释放空间
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6.错误处理，提升程序健壮性,其实就是封装一个错误处理方法
     * <p>
     * 获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //表示代码沙箱错误
        executeCodeResponse.setStatus(3);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
