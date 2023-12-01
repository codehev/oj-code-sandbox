package com.wei.ojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.wei.ojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.io.*;

public class ProcessUtils {
    /**
     * main函数接收参数String[] args
     * 与leetcode方式类似
     * class Solution {
     * public int[] twoSum(int[] nums, int target) {
     * <p>
     * }
     * }
     *
     * @param process 进程
     * @param opName  操作名称
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            //用来计算运行时间，sping的工具类
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int exitValue = process.waitFor();//等待程序执行，获取错误码
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                //正常退出
                System.out.println(opName + "成功\n");
                //分批获取进程的正常输出；BufferedReader成块读取，InputStreamReader读取complieProcess进程的输入流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder compileOutpuStringBuilder = new StringBuilder();
                //按行读取控制台编译信息
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    //System.out.println(compileOutputLine);
                    compileOutpuStringBuilder.append(compileOutputLine);//拼接字符串
                }
                //System.out.println(compileOutpuStringBuilder);
                executeMessage.setMessage(compileOutpuStringBuilder.toString());
            } else {
                //异常退出
                System.out.println(opName + "失败，错误码：" + exitValue + "\n");
                /**
                 * 输入流getInputStream和错误流getErrorStream是程序编写者可以控制的，可能把程序的报错写到输入流中
                 * 所以也要读取输入流
                 */
                //分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder compileOutpuStringBuilder = new StringBuilder();
                //按行读取控制台编译信息
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    //System.out.println(compileOutputLine);
                    compileOutpuStringBuilder.append(compileOutputLine);//拼接字符串
                }
                //System.out.println(compileOutpuStringBuilder);
                executeMessage.setMessage(compileOutpuStringBuilder.toString());

                //分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorCompileOutpuStringBuilder = new StringBuilder();
                //按行读取控制台编译信息
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    //System.out.println(errorCompileOutputLine);
                    errorCompileOutpuStringBuilder.append(errorCompileOutputLine);//拼接字符串
                }
                //System.out.println(errorCompileOutpuStringBuilder);
                executeMessage.setErrorMessage(errorCompileOutpuStringBuilder.toString());
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return executeMessage;
    }

    /**
     * 执行交互式进程并获取信息（键盘输入）
     * 很多OJ都是ACM模式，需要和用户交互的方式，让用户不断输入内容并获取输出
     *
     * @param process 进程
     * @param args    参数
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process process, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            //向控制台输入程序
            //对于此类程序，我们需要使用OutputStream向程序终端发送参数，并及时获取结果，注意最后要关闭流释放资源。
            OutputStream outputStream = process.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");//按空格分割
            // todo 没有参数时，以\n作为输入
            String s1 = StrUtil.join("\n", s) + "\n";//用回车\n拼接
            outputStreamWriter.write(s1);//相当于控制台输入
            outputStreamWriter.flush();//清空缓存，相当于回车


            //分批获取进程的正常输出；BufferedReader成块读取，InputStreamReader读取complieProcess进程的输入流
            InputStream inputStream = process.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutpuStringBuilder = new StringBuilder();
            //按行读取控制台编译信息
            String compileOutputLine;
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                //System.out.println(compileOutputLine);
                compileOutpuStringBuilder.append(compileOutputLine);//拼接字符串
            }
            //System.out.println(compileOutpuStringBuilder);
            executeMessage.setMessage(compileOutpuStringBuilder.toString());
            //记得资源回收，否则会卡死
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
