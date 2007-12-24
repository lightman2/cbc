package net.loveruby.cflat.compiler;
import net.loveruby.cflat.ast.*;
import net.loveruby.cflat.type.*;
import net.loveruby.cflat.asm.*;
import java.util.*;

public class CodeGenerator implements ASTVisitor {
    static public String generate(AST ast) {
        return new CodeGenerator(new Assembler()).generateAssembly(ast);
    }

    protected Assembler as;
    protected DefinedFunction currentFunction;
    protected TypeTable typeTable;

    public CodeGenerator(Assembler as) {
        this.as = as;
    }
static public void p(String s) { System.err.println(s); }

    /** Compiles "ast" and generates assembly code. */
    public String generateAssembly(AST ast) {
        typeTable = ast.typeTable();
        allocateGlobalVariables(ast.globalVariables());
        allocateCommonSymbols(ast.commonSymbols());

        as._file(ast.fileName());
        // .data
        compileGlobalVariables(ast.globalVariables());
        if (!ast.constantTable().isEmpty()) {
            compileConstants(ast.constantTable());
        }
        // .text
        if (ast.functionDefined()) {
            compileFunctions(ast.functions());
        }
        // .bss
        compileCommonSymbols(ast.commonSymbols());

        return as.string();
    }

    /**
     * Sets address for...
     *   * public global variables
     *   * private global variables
     *   * static local variables
     */
    protected void allocateGlobalVariables(Iterator vars) {
        while (vars.hasNext()) {
            Variable var = (Variable)vars.next();
            var.setAddress(globalVariableAddress(var.symbol()));
        }
    }

    /**
     * Sets address for...
     *   * public common symbols
     *   * private common symbols
     */
    protected void allocateCommonSymbols(Iterator comms) {
        while (comms.hasNext()) {
            Variable var = (Variable)comms.next();
            var.setAddress(commonSymbolAddress(var.symbol()));
        }
    }

    /** Linux/IA-32 dependent */
    // FIXME: PIC
    protected AsmEntity globalVariableAddress(String sym) {
        return new Label(csymbol(sym));
    }

    /** Linux/IA-32 dependent */
    // FIXME: PIC
    protected AsmEntity commonSymbolAddress(String sym) {
        return new Label(csymbol(sym));
    }

    /** Generates static variable entries */
    protected void compileGlobalVariables(Iterator vars) {
        as._data();
        while (vars.hasNext()) {
            DefinedVariable var = (DefinedVariable)vars.next();
            dataEntry(var);
        }
    }

    /** Generates initialized entries */
    protected void dataEntry(DefinedVariable ent) {
        if (!ent.isPrivate()) {
            as._globl(csymbol(ent.symbol()));
        }
        as._align(ent.size());
        as._type(csymbol(ent.symbol()), "@object");
        as._size(csymbol(ent.symbol()), ent.size());
        as.label(csymbol(ent.symbol()));
        compileImmediate(ent.type(), ent.initializer());
    }

    /** Generates immediate values for .data section */
    protected void compileImmediate(Type type, Node n) {
        // FIXME: support other constants
        IntegerLiteralNode expr = (IntegerLiteralNode)n;
        switch ((int)type.size()) {
        case 1: as._byte(expr.value());    break;
        case 2: as._value(expr.value());   break;
        case 4: as._long(expr.value());    break;
        case 8: as._quad(expr.value());    break;
        default:
            throw new Error("entry size is not 1,2,4,8");
        }
    }

    /** Generates BSS entries */
    protected void compileCommonSymbols(Iterator ents) {
        while (ents.hasNext()) {
            Variable ent = (Variable)ents.next();
            if (ent.isPrivate()) {
                as._local(csymbol(ent.symbol()));
            }
            as._comm(csymbol(ent.symbol()), ent.size(), ent.alignment());
        }
    }

    /** Generates .rodata entry (constant strings) */
    protected void compileConstants(ConstantTable table) {
        as._section(".rodata");
        Iterator ents = table.entries();
        while (ents.hasNext()) {
            ConstantEntry ent = (ConstantEntry)ents.next();
            as.label(ent.label());
            as._string(ent.value());
        }
    }

    /** Compiles all functions and generates .text section. */
    protected void compileFunctions(Iterator funcs) {
        as._text();
        while (funcs.hasNext()) {
            DefinedFunction func = (DefinedFunction)funcs.next();
            compileFunction(func);
        }
    }

    /** Compiles a function. */
    protected void compileFunction(DefinedFunction func) {
        currentFunction = func;
        String symbol = csymbol(func.name());
        as._globl(symbol);
        as._type(symbol, "@function");
        as.label(symbol);
        prologue(func);
        allocateParameters(func);
        allocateLocalVariables(func);
        compile(func.body());
        epilogue(func);
        as._size(symbol, ".-" + symbol);
    }

    protected void compile(Node n) {
        n.accept(this);
    }

    // platform dependent
    protected String tmpsymbol(String sym) {
        return sym;
    }

    // platform dependent
    protected String csymbol(String sym) {
        return sym;
    }

    protected void prologue(DefinedFunction func) {
        as.pushq(bp());
        as.movq(sp(), bp());
    }

    protected void epilogue(DefinedFunction func) {
        as.label(epilogueLabel(func));
        as.movq(bp(), sp());
        as.popq(bp());
        as.ret();
    }

    protected void jmpEpilogue() {
        as.jmp(new Label(epilogueLabel(currentFunction)));
    }

    protected String epilogueLabel(DefinedFunction func) {
        return ".L" + func.name() + "$epilogue";
    }

    /* Standard IA-32 stack frame layout (after prologue)
     *
     * ======================= esp (stack top)
     * temporary
     * variables...
     * ---------------------   ebp-(4*3)
     * lvar 3
     * ---------------------   ebp-(4*2)
     * lvar 2
     * ---------------------   ebp-(4*1)
     * lvar 1
     * ======================= ebp
     * saved ebp
     * ---------------------   ebp+(4*1)
     * return address
     * ---------------------   ebp+(4*2)
     * arg 1
     * ---------------------   ebp+(4*3)
     * arg 2
     * ---------------------   ebp+(4*4)
     * arg 3
     * ...
     * ...
     * ======================= stack bottom
     */

    /*
     * Platform Dependent Stack Parameters
     */
    static final protected int stackDirection = -1;   // stack grows lower
    static final protected long stackWordSize = 4;
    // 1 for return address, 1 for saved bp.
    static final protected long paramStartOffset = 2;
    static final protected long usedStackWords = 0;

    protected void allocateParameters(DefinedFunction func) {
        Iterator vars = func.parameters();
        long i = paramStartOffset;
        while (vars.hasNext()) {
            Parameter var = (Parameter)vars.next();
            var.setAddress(lvarAddressByWord(i));
            i++;
        }
    }

    protected CompositeAddress lvarAddressByWord(long offset) {
        return new CompositeAddress(offset * stackWordSize, bp());
    }

    protected void allocateLocalVariables(DefinedFunction func) {
        Iterator vars = func.localVariables();
        long len = usedStackWords * stackWordSize;
        while (vars.hasNext()) {
            DefinedVariable var = (DefinedVariable)vars.next();
            if (stackDirection < 0) {
                len = align(len + var.size(), stackWordSize);
                var.setAddress(new CompositeAddress(-len, bp()));
            }
            else {
                var.setAddress(new CompositeAddress(len, bp()));
                len = align(len + var.size(), stackWordSize);
            }
        }
        if (len != 0) {
            extendStack(len);
        }
    }

    protected void extendStack(long len) {
        as.addq(imm(stackDirection * len), sp());
    }

    protected void shrinkStack(long len) {
        as.subq(imm(stackDirection * len), sp());
    }

    protected long align(long n, long alignment) {
        return (n + alignment - 1) / alignment * alignment;
    }

    protected Register bp() {
        return reg("bp");
    }

    protected Register sp() {
        return reg("sp");
    }

    // cdecl call
    //     * all arguments are on stack
    //     * rollback stack by caller
    public void visit(FuncallNode node) {
        ListIterator it = node.finalArg();
        while (it.hasPrevious()) {
            Node arg = (Node)it.previous();
            compile(arg);
            as.pushq(reg("ax"));
        }
        //if (node.function().isVararg()) {
        //    ...
        //}
        if (node.isStaticCall()) {
            if (node.function().isDefined()) {
                as.call(csymbol(node.function().name()));
            }
            else {
                as.call(tmpsymbol(node.function().name()));
            }
        }
        else {  // funcall via pointer
            // FIXME
            compile(node.expr());
            as.ptrcall(reg("ax"));
        }
        if (node.numArgs() > 0) {
            // FIXME: >4 size arguments are not supported.
            shrinkStack(node.numArgs() * stackWordSize);
        }
    }

    public void visit(ReturnNode node) {
        if (node.expr() != null) {
            compile(node.expr());
        }
        jmpEpilogue();
    }

    public void visit(BlockNode node) {
        Iterator vars = node.scope().variables();
        while (vars.hasNext()) {
            DefinedVariable var = (DefinedVariable)vars.next();
            if (var.initializer() != null) {
                compile(var.initializer());
                saveWords(var.type(), "ax", var.address());
            }
        }
        Iterator stmts = node.stmts();
        while (stmts.hasNext()) {
            compileStmt((Node)stmts.next());
        }
    }

    // needed?
    protected void compileStmt(Node node) {
        compile(node);
    }

    public void visit(CastNode node) {
        // FIXME: insert cast op here
        compile(node.expr());
    }

    public void visit(IntegerLiteralNode node) {
        as.mov(node.type(), imm(node.value()), reg("ax", node.type()));
    }

    public void visit(CharacterLiteralNode node) {
        as.mov(node.type(), imm(node.value()), reg("ax", node.type()));
    }

    public void visit(StringLiteralNode node) {
        loadWords(node.type(), imm(node.label()), "ax");
    }

    public void visit(VariableNode node) {
        loadWords(node.type(), node.address(), "ax");
    }

    static final String PTRREG = "bx";

    public void visit(ArefNode node) {
        compileLHS(node.expr());
        as.pushq(reg(PTRREG));
        compile(node.index());
        as.movq(reg("ax"), reg("cx"));
        as.popq(reg(PTRREG));
        as.imulq(imm(node.type().size()), reg("cx"));
        as.addq(reg("cx"), reg(PTRREG));
        loadWords(node.type(), addr(PTRREG), "ax");
    }

    public void visit(MemberNode node) {
        compileLHS(node.expr());
        loadWords(node.type(), addr2(node.offset(), PTRREG), "ax");
    }

    public void visit(PtrMemberNode node) {
        compileLHS(node.expr());
        loadWords(node.type(), addr(PTRREG), PTRREG);
        loadWords(node.type(), addr2(node.offset(), PTRREG), "ax");
    }

    public void visit(AssignNode node) {
        // FIXME
        if (((LHSNode)node.lhs()).isConstantAddress()) {
            compile(node.rhs());
            saveWords(node.type(), "ax", node.lhs().address());
        }
        else {
            compile(node.rhs());
            as.pushq(reg("ax"));
            compileLHS(node.lhs());
            // leave RHS-value on the stack
            as.movq(addr("sp"), reg("ax"));
            saveWords(node.type(), "ax", addr(PTRREG));
        }
    }

    public void visit(DereferenceNode node) {
        compileLHS(node.expr());
        as.movq(addr(PTRREG), reg("ax"));
        loadWords(node.type(), addr("ax"), "ax");
    }

    public void visit(AddressNode node) {
        compileLHS(node.expr());
        as.movq(reg(PTRREG), reg("ax"));
    }

    protected void compileLHS(Node node) {
        if (node instanceof VariableNode) {
            // FIXME: support static variables
            VariableNode n = (VariableNode)node;
            as.leaq(n.address(), reg(PTRREG));
        }
        else if (node instanceof ArefNode) {
            // FIXME: support non-constant index
            ArefNode n = (ArefNode)node;
            //as.movq(imm(n.index().value()), reg(PTRREG));  // FIXME
            as.movq(imm(((IntegerLiteralNode)n.index()).value()), reg(PTRREG));
            as.imulq(imm(n.type().size()), reg(PTRREG));   // unsigned?
            as.pushq(reg(PTRREG));
            compileLHS(n.expr());
            as.popq(reg("cx"));
            as.addq(reg("cx"), reg(PTRREG));
        }
        else if (node instanceof MemberNode) {
            MemberNode n = (MemberNode)node;
            compileLHS(n.expr());
            as.addq(imm(n.offset()), reg(PTRREG));
        }
        else if (node instanceof DereferenceNode) {
            DereferenceNode n = (DereferenceNode)node;
            compileLHS(n.expr());
            as.movq(addr(PTRREG), reg(PTRREG));
        }
        else if (node instanceof PtrMemberNode) {
            PtrMemberNode n = (PtrMemberNode)node;
            compileLHS(n.expr());
            as.movq(addr(PTRREG), reg(PTRREG));
            as.addq(imm(n.offset()), reg(PTRREG));
        }
        else if (node instanceof PrefixIncNode) {
            PrefixIncNode n = (PrefixIncNode)node;
            compileLHS(n.expr());
            as.addq(imm(n.expr().type().size()), reg(PTRREG));
        }
        else if (node instanceof PrefixDecNode) {
            PrefixDecNode n = (PrefixDecNode)node;
            compileLHS(n.expr());
            as.subq(imm(n.expr().type().size()), reg(PTRREG));
        }
// FIXME
        //} else if (node instanceof SuffixIncNode) {
        //} else if (node instanceof SuffixDecNode) {
        else {
            throw new Error("wrong type for compileLHS");
        }
    }

    protected void opAssignInit(AbstractAssignNode node) {
        compile(node.rhs());
        as.movq(reg("ax"), reg("cx"));
        loadWords(node.type(), node.lhs().address(), "ax");
    }

    protected void opAssignFini(AbstractAssignNode node) {
        saveWords(node.type(), "ax", node.lhs().address());
    }

    public void visit(PlusAssignNode node) {
        opAssignInit(node);
        compile_plus(node.type());
        opAssignFini(node);
    }

    public void visit(MinusAssignNode node) {
        opAssignInit(node);
        compile_minus(node.type());
        opAssignFini(node);
    }

    public void visit(MulAssignNode node) {
        opAssignInit(node);
        compile_mul(node.type());
        opAssignFini(node);
    }

    public void visit(DivAssignNode node) {
        opAssignInit(node);
        compile_div(node.type());
        opAssignFini(node);
    }

    public void visit(ModAssignNode node) {
        opAssignInit(node);
        compile_mod(node.type());
        opAssignFini(node);
    }

    public void visit(RShiftAssignNode node) {
        opAssignInit(node);
        compile_rshift(node.type());
        opAssignFini(node);
    }

    public void visit(LShiftAssignNode node) {
        opAssignInit(node);
        compile_lshift(node.type());
        opAssignFini(node);
    }

    public void visit(AndAssignNode node) {
        opAssignInit(node);
        compile_bitwiseand(node.type());
        opAssignFini(node);
    }

    public void visit(OrAssignNode node) {
        opAssignInit(node);
        compile_bitwiseor(node.type());
        opAssignFini(node);
    }

    public void visit(XorAssignNode node) {
        opAssignInit(node);
        compile_bitwisexor(node.type());
        opAssignFini(node);
    }

    protected void binaryOpSetup(BinaryOpNode node) {
        compile(node.right());
        as.pushq(reg("ax"));
        compile(node.left());
        as.popq(reg("cx"));
    }

    public void visit(PlusNode node) {
        binaryOpSetup(node);
        compile_plus(node.type());
    }

    public void visit(MinusNode node) {
        binaryOpSetup(node);
        compile_minus(node.type());
    }

    public void visit(MulNode node) {
        binaryOpSetup(node);
        compile_mul(node.type());
    }

    public void visit(DivNode node) {
        binaryOpSetup(node);
        compile_div(node.type());
    }

    public void visit(ModNode node) {
        binaryOpSetup(node);
        compile_mod(node.type());
    }

    public void visit(BitwiseAndNode node) {
        binaryOpSetup(node);
        compile_bitwiseand(node.type());
    }

    public void visit(BitwiseOrNode node) {
        binaryOpSetup(node);
        compile_bitwiseor(node.type());
    }

    public void visit(BitwiseXorNode node) {
        binaryOpSetup(node);
        compile_bitwisexor(node.type());
    }

    public void visit(RShiftNode node) {
        binaryOpSetup(node);
        compile_rshift(node.type());
    }

    public void visit(LShiftNode node) {
        binaryOpSetup(node);
        compile_lshift(node.type());
    }

    public void visit(EqNode node) {
        binaryOpSetup(node);
        compile_eq(node.type());
    }

    public void visit(NotEqNode node) {
        binaryOpSetup(node);
        compile_neq(node.type());
    }

    public void visit(GtNode node) {
        binaryOpSetup(node);
        compile_gt(node.type());
    }

    public void visit(LtNode node) {
        binaryOpSetup(node);
        compile_lt(node.type());
    }

    public void visit(GtEqNode node) {
        binaryOpSetup(node);
        compile_gteq(node.type());
    }

    public void visit(LtEqNode node) {
        binaryOpSetup(node);
        compile_lteq(node.type());
    }

    protected void compile_plus(Type t) {
        as.add(t, reg("cx", t), reg("ax", t));
    }

    protected void compile_minus(Type t) {
        as.sub(t, reg("cx", t), reg("ax", t));
    }

    protected void compile_mul(Type t) {
        as.imul(t, reg("cx", t), reg("ax", t));
    }

    protected void compile_div(Type t) {
        as.movq(imm(0), reg("dx"));
        as.idiv(t, reg("cx", t));
    }

    protected void compile_mod(Type t) {
        as.movq(imm(0), reg("dx"));
        as.idiv(t, reg("cx", t));
        as.movq(reg("dx"), reg("ax"));
    }

    protected void compile_bitwiseand(Type t) {
        as.and(t, reg("cx", t), reg("ax", t));
    }

    protected void compile_bitwiseor(Type t) {
        as.or(t, reg("cx", t), reg("ax", t));
    }

    protected void compile_bitwisexor(Type t) {
        as.xor(t, reg("cx", t), reg("ax", t));
    }

    protected void compile_rshift(Type t) {
        as.sar(t, register("cl"), reg("ax", t));
    }

    protected void compile_lshift(Type t) {
        as.sal(t, register("cl"), reg("ax", t));
    }

    protected void compile_eq(Type t) {
        as.cmp(t, reg("cx", t), reg("ax", t));
        as.sete(register("al"));
        as.movzb(t, register("al"), reg("ax", t));
    }

    protected void compile_neq(Type t) {
        as.cmp(t, reg("cx", t), reg("ax", t));
        as.setne(register("al"));
        as.movzb(t, register("al"), reg("ax", t));
    }

    protected void compile_gt(Type t) {
        as.cmp(t, reg("cx", t), reg("ax", t));
        as.setg(register("al"));
        as.movzb(t, register("al"), reg("ax", t));
    }

    protected void compile_lt(Type t) {
        as.cmp(t, reg("cx", t), reg("ax", t));
        as.setl(register("al"));
        as.movzb(t, register("al"), reg("ax", t));
    }

    protected void compile_gteq(Type t) {
        as.cmp(t, reg("cx", t), reg("ax", t));
        as.setge(register("al"));
        as.movzb(t, register("al"), reg("ax", t));
    }

    protected void compile_lteq(Type t) {
        as.cmp(t, reg("cx", t), reg("ax", t));
        as.setle(register("al"));
        as.movzb(t, register("al"), reg("ax", t));
    }

    public void visit(UnaryPlusNode node) {
        compile(node.expr());
    }

    public void visit(UnaryMinusNode node) {
        compile(node.expr());
        as.neg(node.expr().type(), reg("ax", node.expr().type()));
    }

    public void visit(LogicalNotNode node) {
        compile(node.expr());
        testCond(node.expr().type(), "ax");
        as.sete(register("al"));
        as.movzbl(register("al"), register("eax"));
    }

    public void visit(BitwiseNotNode node) {
        compile(node.expr());
        as.not(node.expr().type(), reg("ax", node.expr().type()));
    }

    public void visit(PrefixIncNode node) {
        Node e = node.expr();
        as.inc(e.type(), e.address());
        loadWords(e.type(), e.address(), "ax");
    }

    public void visit(PrefixDecNode node) {
        Node e = node.expr();
        as.dec(e.type(), e.address());
        loadWords(e.type(), e.address(), "ax");
    }

    public void visit(SuffixIncNode node) {
        Node e = node.expr();
        loadWords(e.type(), e.address(), "ax");
        as.inc(e.type(), e.address());
    }

    public void visit(SuffixDecNode node) {
        Node e = node.expr();
        loadWords(e.type(), e.address(), "ax");
        as.dec(e.type(), e.address());
    }

    private void testCond(Type t, String regname) {
        as.test(t, reg(regname, t), reg(regname, t));
    }

    public void visit(IfNode node) {
        compile(node.cond());
        testCond(node.cond().type(), "ax");
        if (node.elseBody() != null) {
            as.jz(node.elseLabel());
            compileStmt(node.thenBody());
            as.jmp(node.endLabel());
            as.label(node.elseLabel());
            compileStmt(node.elseBody());
            as.label(node.endLabel());
        }
        else {
            as.jz(node.endLabel());
            compileStmt(node.thenBody());
            as.label(node.endLabel());
        }
    }

    public void visit(CondExprNode node) {
        compile(node.cond());
        testCond(node.cond().type(), "ax");
        as.jz(node.elseLabel());
        compile(node.thenBody());
        as.jmp(node.endLabel());
        as.label(node.elseLabel());
        compile(node.elseBody());
        as.label(node.endLabel());
    }

    public void visit(SwitchNode node) {
        compile(node.cond());
        Type t = typeTable.signedInt();
        Iterator cases = node.cases();
        while (cases.hasNext()) {
            CaseNode caseNode = (CaseNode)cases.next();
            Iterator values = caseNode.values();
            while (values.hasNext()) {
                as.movq(imm(caseValue((Node)values.next())), reg("cx"));
                as.cmp(t, reg("cx", t), reg("ax", t));
                as.je(caseNode.beginLabel());
            }
        }
        cases = node.cases();
        while (cases.hasNext()) {
            compile((CaseNode)cases.next());
        }
    }

    protected long caseValue(Node node) {
        if (!(node instanceof IntegerLiteralNode)) {
            // FIXME: use exception
            throw new Error("case accepts only integer literal");
        }
        return ((IntegerLiteralNode)node).value();
    }

    public void visit(CaseNode node) {
        as.label(node.beginLabel());
        compile(node.body());
    }

    public void visit(LogicalAndNode node) {
        compile(node.left());
        testCond(node.left().type(), "ax");
        as.jz(node.endLabel());
        compile(node.right());
        as.label(node.endLabel());
    }

    public void visit(LogicalOrNode node) {
        compile(node.left());
        testCond(node.left().type(), "ax");
        as.jnz(node.endLabel());
        compile(node.right());
        as.label(node.endLabel());
    }

    public void visit(WhileNode node) {
        as.label(node.begLabel());
        compile(node.cond());
        testCond(node.cond().type(), "ax");
        as.jz(node.endLabel());
        compileStmt(node.body());
        as.jmp(node.begLabel());
        as.label(node.endLabel());
    }

    public void visit(DoWhileNode node) {
        as.label(node.begLabel());
        compileStmt(node.body());
        as.label(node.continueLabel());
        compile(node.cond());
        testCond(node.cond().type(), "ax");
        as.jnz(node.begLabel());
        as.label(node.endLabel());
    }

    public void visit(ForNode node) {
        compileStmt(node.init());
        as.label(node.begLabel());
        compile(node.cond());
        testCond(node.cond().type(), "ax");
        as.jz(node.endLabel());
        compileStmt(node.body());
        as.label(node.continueLabel());
        compileStmt(node.incr());
        as.jmp(node.begLabel());
        as.label(node.endLabel());
    }

    public void visit(BreakNode node) {
        as.jmp(node.targetLabel());
    }

    public void visit(ContinueNode node) {
        as.jmp(node.targetLabel());
    }

    public void visit(LabelNode node) {
        as.label(node.label());
        compileStmt(node.stmt());
    }

    public void visit(GotoNode node) {
        as.jmp(node.targetLabel());
    }

    /*
     *  x86 assembly DSL
     */

    protected Register reg(String name, Type type) {
        return Register.forType(type, name);
    }

    protected Register reg(String name) {
        return Register.widestRegister(name);
    }

    protected Register register(String name) {
        return new Register(name);
    }

    protected SimpleAddress addr(String regname) {
        return new SimpleAddress(Register.widestRegister(regname));
    }

    protected CompositeAddress addr2(long offset, String regname) {
        return new CompositeAddress(offset, Register.widestRegister(regname));
    }

    protected ImmediateValue imm(long n) {
        return new ImmediateValue(n);
    }

    protected Reference imm(Label label) {
        return new Reference(label);
    }

    protected void loadWords(Type type, AsmEntity addr, String reg) {
        switch ((int)type.size()) {
        case 1:
            if (type.isSigned()) {  // signed char
                as.movsbl(addr, intReg(reg));
            } else {                // unsigned char
                as.movzbl(addr, intReg(reg));
            }
            break;
        case 2:
            if (type.isSigned()) {  // signed short
                as.movswl(addr, intReg(reg));
            } else {                // unsigned short
                as.movzwl(addr, intReg(reg));
            }
            break;
        default:                    // int, long, long_long
            as.mov(type, addr, reg(reg, type));
            break;
        }
    }

    protected Register intReg(String reg) {
        return new Register("e" + reg);
    }

    protected void saveWords(Type type, String reg, AsmEntity addr) {
        as.mov(type, reg(reg, type), addr);
    }
}