package com.wei.ojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.wei.ojcodesandbox.utils.DockerClientUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * @author wei
 */
public class DockerDemo {
    @Value("${docker.host}")
    private static String DOCKER_HOST;

    @Value("${docker.api-version}")
    private static String DOCKER_API_VERSION;

    public static void main(String[] args) throws InterruptedException {
        String DOCKER_HOST = "tcp://192.168.200.139:2375";
        String DOCKER_API_VERSION = "1.43";

        //获取默认的 Docker client
        //DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        DockerClient dockerClient = DockerClientUtils.connect(DOCKER_HOST, DOCKER_API_VERSION);
        PingCmd pingCmd = dockerClient.pingCmd();
        System.out.println(pingCmd.exec());

        /**
         * 拉取镜像
         */
        String image = "nginx:latest";
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        //定义回调函数。镜像拉取完后干什么事情
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            /**
             * 镜像每下载一部分（镜像的layer）会调用该方法
             * @param item
             */
            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("下载状态：" + item.getStatus());
                super.onNext(item);
            }
        };
        //异步的方式执行,awaitCompletion,阻塞，直到下载完成（没下载完，会一直卡在那），不然不会等下载完成就往下执行
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        System.out.println("下载完成");


        /**
         * 创建容器
         */
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                //容器启动时执行的命令"echo"，并传递了一个字符串参数"Hello Docker"。效果是容器日志中有Hello Docker
                //相当于执行docker exec：进入容器执行命令，容器里面相当于一个Linux
                .withCmd("echo", "Hello Docker")
                .exec();
        System.out.println("createContainerResponse：" + createContainerResponse);
        String containerId = createContainerResponse.getId();

        /**
         * 查看容器状态
         */
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println("容器状态：" + container);
        }

        /**
         * 启动容器
         * 异步执行，不会等到容器启动完成，只是发送启动请求
         */
        dockerClient.startContainerCmd(containerId).exec();

        /**
         * 查看日志
         */
        dockerClient.logContainerCmd(containerId)
                //获取容器的标准输出和标准错误流
                .withStdOut(true)
                .withStdErr(true)
                .exec(new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        System.out.println("日志：" + item.toString());
                        super.onNext(item);
                    }
                }).awaitCompletion();
                //阻塞等待

        /**
         * 删除容器
         */
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        /**
         * 删除镜像
         */
        dockerClient.removeImageCmd(image).exec();
    }
}
