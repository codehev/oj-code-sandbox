package com.wei.ojcodesandbox.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.IOException;
import java.time.Duration;



public class DockerClientUtils {

    private static DockerClient dockerClient;

    private DockerClientUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 连接docker服务器
     *
     * @param dockerHost       Docker 主机
     * @param dockerApiVersion Docker API 版本
     * @return DockerClient 实例
     */
    public static DockerClient connect(String dockerHost, String dockerApiVersion) {
        if (dockerClient == null) {
            // 配置docker CLI的一些选项
            DefaultDockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
//                .withDockerTlsVerify(DOCKER_TLS_VERIFY)
                    .withDockerHost(dockerHost)
                    // 与docker版本对应，参考https://docs.docker.com/engine/api/#api-version-matrix
                    // 或者通过docker version指令查看api version
                    .withApiVersion(dockerApiVersion)
//                .withRegistryUrl(REGISTRY_URL)
                    .build();

            // 创建DockerHttpClient
            DockerHttpClient httpClient = new ApacheDockerHttpClient
                    .Builder()
                    .dockerHost(config.getDockerHost())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            /*Info info = dockerClient.infoCmd().exec();
            String infoStr = JSONObject.toJSONString(info);
            System.out.println("docker环境信息");
            System.out.println(infoStr);*/

        }
        return dockerClient;
    }

    /**
     * 关闭 DockerHttpClient，释放资源
     */
    public static void close() throws IOException {
        if (dockerClient != null) {
            DockerHttpClient httpClient = ((DockerClientImpl) dockerClient).getHttpClient();
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }
}
