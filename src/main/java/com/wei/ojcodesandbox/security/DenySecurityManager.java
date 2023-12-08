package com.wei.ojcodesandbox.security;

import java.security.Permission;

/**
 * 禁用所有权限安全管理器
 *
 * SecurityManager不太推荐在java9以上版本使用，慢慢会被取代
 */
public class DenySecurityManager extends SecurityManager {

    // 检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
        //直接抛出异常，相当于禁用所有异常
        throw new SecurityException("权限异常：" + perm.toString());
    }
}
