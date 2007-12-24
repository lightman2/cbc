package net.loveruby.cflat.ast;
import net.loveruby.cflat.type.*;
import net.loveruby.cflat.asm.*;

public class StringLiteralNode extends Node {
    protected TypeNode typeNode;
    protected String value;
    protected ConstantEntry entry;

    public StringLiteralNode(TypeRef ref, String s) {
        typeNode = new TypeNode(ref);
        value = s;
    }

    public TypeNode typeNode() {
        return typeNode;
    }

    public Type type() {
        return typeNode.type();
    }

    public String value() {
        return value;
    }

    public void setEntry(ConstantEntry ent) {
        entry = ent;
    }

    public Label label() {
        checkEntry();
        return entry.label();
    }

    protected long id() {
        checkEntry();
        return entry.id();
    }

    protected void checkEntry() {
        if (entry == null)
            throw new Error("StringLiteralNode#entry not resolved");
    }

    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}