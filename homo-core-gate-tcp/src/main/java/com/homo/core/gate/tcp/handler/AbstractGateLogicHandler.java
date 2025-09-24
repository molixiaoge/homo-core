package com.homo.core.gate.tcp.handler;

import com.homo.core.facade.gate.GateClient;
import com.homo.core.facade.gate.GateMessageHeader;
import com.homo.core.facade.gate.GateMessagePackage;
import com.homo.core.gate.tcp.TcpGateDriver;
import com.homo.core.utils.trace.ZipkinUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;



@ChannelHandler.Sharable
@Slf4j
public abstract class AbstractGateLogicHandler<T> extends ChannelInboundHandlerAdapter {

    public abstract void doProcess(T data, GateClient gateClient, GateMessageHeader header)throws Exception ;
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object source) throws Exception {
        GateMessagePackage messagePackage = (GateMessagePackage) source;
        GateMessageHeader header = messagePackage.getHeader();
        GateClient gateClient = ctx.channel().attr(TcpGateDriver.clientKey).get();
        // 统一的抽象层不做埋点，具体协议处理器（Proto/JSON）负责按消息维度打 SR/SS
        doProcess((T) messagePackage, gateClient, header);
    }

}
