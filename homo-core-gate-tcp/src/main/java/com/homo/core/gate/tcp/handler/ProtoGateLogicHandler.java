package com.homo.core.gate.tcp.handler;

import com.homo.core.facade.gate.GateClient;
import com.homo.core.facade.gate.GateMessageHeader;
import com.homo.core.facade.gate.GateMessagePackage;
import com.homo.core.facade.gate.GateMessageType;
import com.homo.core.gate.tcp.TcpGateDriver;
import com.homo.core.utils.trace.ZipkinUtil;
import io.homo.proto.client.Msg;
import io.netty.channel.ChannelHandlerContext;

public abstract class ProtoGateLogicHandler extends AbstractGateLogicHandler<Msg> {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object source) throws Exception {
        GateMessagePackage messagePackage = (GateMessagePackage) source;
        GateMessageHeader header = messagePackage.getHeader();
        if (header.getType() == GateMessageType.PROTO.ordinal()) {
            Msg msg = Msg.parseFrom(messagePackage.getBody());
            GateClient gateClient = ctx.channel().attr(TcpGateDriver.clientKey).get();
            // SR at inbound; SS after processing
            ZipkinUtil.startScope(
                    ZipkinUtil.nextOrCreateSRSpan(),
                    span -> doProcess(msg, gateClient, header),
                    span -> span.annotate(ZipkinUtil.SERVER_SEND_TAG).finish()
            );
        } else {
            //不是proto数据 交给下一个handler处理
            ctx.fireChannelRead(source);
        }
    }
}
