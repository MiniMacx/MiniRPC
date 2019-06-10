package org.tustcs.minirpc.server;


import org.tustcs.minirpc.client.HelloService;

@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(String name) {
        String s= "Hello! " + name;
        System.out.println(s);
        return s;
    }
}


