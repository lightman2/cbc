package net.loveruby.cflat.ast;
import net.loveruby.cflat.type.*;
import java.util.*;

public class FixedParams extends Params {
    protected List parameters;

    public FixedParams(List params) {
        parameters = params;
    }

    public Iterator parameters() {
        return parameters.iterator();
    }

    protected List parametersList() {
        return parameters;
    }

    public boolean isVararg() {
        return false;
    }

    public boolean equals(Object other) {
        if (!(other instanceof FixedParams)) return false;
        FixedParams params = (FixedParams)other;
        return parameters.equals(params.parametersList());
    }

    public Params internTypes(TypeTable table) {
        Iterator it = parameters.iterator();
        List types = new ArrayList();
        while (it.hasNext()) {
            types.add(table.get((TypeRef)it.next()));
        }
        return new FixedParams(types);
    }

    public Params typeRefs() {
        Iterator it = parameters.iterator();
        List typerefs = new ArrayList();
        while (it.hasNext()) {
            Parameter param = (Parameter)it.next();
            typerefs.add(param.typeNode().typeRef());
        }
        return new FixedParams(typerefs);
    }
}