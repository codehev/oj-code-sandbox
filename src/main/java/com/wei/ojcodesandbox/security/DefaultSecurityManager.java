package com.wei.ojcodesandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 *
 * SecurityManager不太推荐在java9以上版本使用，慢慢会被取代
 * @author whw12
 */
public class DefaultSecurityManager extends SecurityManager{

    /**
     * 检查所有权限
     * @param perm   the requested permission.
     */
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何限制");
        //只要调用该方法，就是禁用所有权限，意味着安全管理器默认是禁用权限
        super.checkPermission(perm);
    }
}
