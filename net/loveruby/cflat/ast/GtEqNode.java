package net.loveruby.cflat.ast;

public class GtEqNode extends BinaryOpNode {
    public GtEqNode(Node left, Node right) {
        super(left, right);
    }

    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}