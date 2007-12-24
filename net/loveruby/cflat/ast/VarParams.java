package net.loveruby.cflat.ast;
import net.loveruby.cflat.type.*;
import java.util.*;

public class VarParams extends Params {
    protected FixedParams params;

    public VarParams(FixedParams params) {
        this.params = params;
    }

    public Iterator parameters() {
        return params.parameters();
    }

    public boolean isVararg() {
        return true;
    }

    protected FixedParams fixedParams() {
        return params;
    }

    public boolean equals(Object other) {
        if (!(other instanceof VarParams)) return false;
        VarParams params = (VarParams)other;
        return params.equals(params.fixedParams());
    }

    public Params internTypes(TypeTable table) {
        return new VarParams((FixedParams)params.internTypes(table));
    }

    public Params typeRefs() {
        return new VarParams((FixedParams)params.typeRefs());
    }
}