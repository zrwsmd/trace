package com.yt.server.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TimerWrapper implements InvocationHandler {
    private Object target;  //被代理的对象
    public TimerWrapper(Object target){
        this.target = target;
    }
    //利用Proxy类的newProxyInstance()方法生成代理对象
    //3个参数(类加载器 ， 代理对象的接口(=被代理对象的接口) ，InvocationHandler对象)
    public Object getProxy(){
        return Proxy.newProxyInstance(TimerWrapper.class.getClassLoader()
                ,target.getClass().getInterfaces(),this);
    }
    @Override
    //3个参数(代理对象 ， 当前执行的方法 ，方法的参数)
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        long startTime = System.currentTimeMillis();//代理人的任务

        Object result = method.invoke(target, args); //被代理人的任务

        long endTime = System.currentTimeMillis();//代理人的任务
        String className = target.getClass().getSimpleName();
        String methodName = method.getName();
        System.out.println(className + "." + methodName + "消耗了:" + (endTime - startTime) + "毫秒");//代理人的任务

        return result;

    }
}
