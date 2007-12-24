package net.loveruby.cflat.ast;

public class LtEqNode extends BinaryOpNode {
    public LtEqNode(Node left, Node right) {
        super(left, right);
    }

    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}