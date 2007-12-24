package net.loveruby.cflat.type;
import net.loveruby.cflat.ast.Params;
import java.util.*;

public class FunctionType extends Type {
    protected Type returnType;
    protected Params paramTypes;

    public FunctionType(Type ret, Params partypes) {
        returnType = ret;
        paramTypes = partypes;
    }

    public Type returnType() {
        return returnType;
    }

    public boolean isVararg() {
        return paramTypes.isVararg();
    }

    public boolean isFunction() {
        return true;
    }

    public long alignment() {
        throw new Error("FunctionType#alignment called");
    }

    public long size() {
        throw new Error("FunctionType#size called");
    }

    public String textize() {
        StringBuffer buf = new StringBuffer();
        buf.append(returnType.textize());
        buf.append(" (*)(");
        Iterator params = paramTypes.parameters();
        while (params.hasNext()) {
            Type t = (Type)params.next();
            buf.append(t.textize());
        }
        buf.append(")");
        return buf.toString();
    }
}