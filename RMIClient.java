package com.zhuzimiko;

import javax.naming.InitialContext;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIClient {
    public static  void  main(String[] args) throws Exception {
        InitialContext initialContext = new InitialContext();
        RemoteObj remoteObj = (RemoteObj)initialContext.lookup("rmi://localhost:1099/remoteObj");
        //Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
        //RemoteObj remoteObj = (RemoteObj) registry.lookup("remoteObj");
        remoteObj.sayHello("hello");
    }
}