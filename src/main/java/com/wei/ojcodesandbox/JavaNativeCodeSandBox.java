package com.wei.ojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wei.ojcodesandbox.model.ExecuteCodeRequest;
import com.wei.ojcodesandbox.model.ExecuteCodeResponse;
import com.wei.ojcodesandbox.model.ExecuteMessage;
import com.wei.ojcodesandbox.model.JudgeInfo;
import com.wei.ojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 此实现类，是在main函数的参数String[] args接收参数，而非代码中键盘输入参数
 */
public class JavaNativeCodeSandBox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
    //用户输入代码的类名限制为 Main（参考 Poj）,可以减少编译时类名不一致的风险,而且不用从用户代码中提取类名，更方便。
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        //1 把用户的代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();//存放代码目录路径
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;//代码路径
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);//代码文件

        //2 编译代码，得到 class 文件
        //控制台编码与java文件编码可能不一致，指定编译编码-encoding utf-8，解决编译乱码（输出结果中文乱码）
        //%s类似于C语言的printf格式化输出
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);//执行命令行命令
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
//            throw new RuntimeException(e);
            return getErrorResponse(e);
        }

        //3 执行代码，得到输出结果,第一个%s目录路径，第二个%s输入参数列表（java代码 main函数的参数String[] args）
        //-Dfile.encoding=UTF-8解决运行乱码（输出结果中文乱码）
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);//执行命令行命令
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
//                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage);
            } catch (Exception e) {
//                throw new RuntimeException(e);
                return getErrorResponse(e);
            }
        }

        //4 收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;//取用时最大值，便与判断是否超时
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {//错误信息非空
                executeCodeResponse.setMessage(errorMessage);
                //执行过程中存在错误，3代表失败
                executeCodeResponse.setStatus(3);
                break;
            }
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            //执行过程中不存在错误
            outputList.add(executeMessage.getMessage());
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1); //2代表成功
        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setMessage();//非程序执行结果输出的信息,在判题服务例设置，此处不设置
//        judgeInfo.setMemory();//比较难实现获取内存
        judgeInfo.setTime(maxTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);


        //5 文件清理，释放空间
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }


        return null;
    }

    /**
     * //6 错误处理，提升程序健壮性,其实就是封装一个错误处理方法
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
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}