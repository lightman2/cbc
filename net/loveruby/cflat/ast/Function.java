package net.loveruby.cflat.ast;
import net.loveruby.cflat.type.*;
import net.loveruby.cflat.asm.*;
import java.util.*;

abstract public class Function extends Entity {
    public Function(boolean priv, TypeNode t, String name) {
        super(priv, t, name);
    }

    public boolean isFunction() { return true; }
    public boolean isInitialized() { return true; }
    abstract public boolean isDefined();
    abstract public Iterator parameters();
    abstract public AsmEntity address();
}