package vproxy.app.args;

import vproxy.app.Application;
import vproxy.app.MainCtx;
import vproxy.app.MainOp;
import vproxy.app.cmd.CmdResult;
import vproxy.app.cmd.SystemCommand;
import vproxy.util.BlockCallback;

public class RespControllerOp implements MainOp {
    @Override
    public String key() {
        return "resp-controller";
    }

    @Override
    public int argCount() {
        return 2;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public int pre(MainCtx ctx, String[] args) {
        if (Application.get().respControllerHolder.names().contains("resp-controller")) {
            System.err.println("resp-controller already set in startup arguments");
            return 1;
        }
        return 0;
    }

    @Override
    public int execute(MainCtx ctx, String[] args) {
        ctx.set("hasRespController", true);
        //noinspection StringBufferReplaceableByString
        StringBuilder call = new StringBuilder();
        call.append("System call: add ")
            .append("resp-controller")
            .append(" (")
            .append("resp-controller")
            .append(") address ")
            .append(args[0])
            .append(" password ")
            .append(args[1]);
        BlockCallback<CmdResult, Throwable> cb = new BlockCallback<>();
        SystemCommand.handleSystemCall(call.toString(), cb);
        try {
            cb.block();
        } catch (Throwable e) {
            System.err.println("start resp-controller failed");
            e.printStackTrace();
            return 1;
        }
        return 0;
    }
}
