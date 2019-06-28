package org.tustcs.minirpc.server;


import org.apache.thrift.TException;
import org.tustcs.minirpc.client.DemoService;

@RpcService(DemoService.Iface.class)
public class DemoServiceImpl implements DemoService.Iface {
    @Override
    public String sayHello(String word) throws TException {
        return (word + "YYYYYYYYYYY");
    }
}
