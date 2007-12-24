package net.loveruby.cflat.type;
import net.loveruby.cflat.ast.*;
import java.util.*;

public class FunctionTypeRef extends TypeRef {
    protected TypeRef returnType;
    protected Params params;

    public FunctionTypeRef(TypeRef returnType, Params params) {
        this.returnType = returnType;
        this.params = params;
    }

    public boolean isFunctionTypeRef() {
        return true;
    }

    public boolean equals(Object other) {
        if (!(other instanceof FunctionTypeRef)) return false;
        FunctionTypeRef ref = (FunctionTypeRef)other;
        return returnType.equals(ref.returnType()) &&
               params.equals(ref.params());
    }

    public int hashCode() {
        return (1 << 13) ^ returnType.hashCode();
    }

    public TypeRef returnType() {
        return returnType;
    }

    public Params params() {
        return params;
    }
}