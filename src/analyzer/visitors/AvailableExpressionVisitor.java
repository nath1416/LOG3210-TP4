package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This Java code defines a visitor class called AvailableExpressionVisitor for a custom parser.
 * The visitor class traverses an abstract syntax tree (AST) and generates optimised code (Common expression elimination).
 * The code includes implementations for various types of assignment statements, such as direct assignment,
 * unary assignment, and assignment with arithmetic operations.
 *
 */
public class AvailableExpressionVisitor implements ParserVisitor {
    private PrintWriter m_writer = null;
    private final ArrayList<String> RETURNS = new ArrayList<>();
    private final ArrayList<CFGNode> CFG = new ArrayList<>();

    // TODO: Choisir la structure de donnée pour votre algorithme
    private final ArrayDeque<CFGNode> worklist = new ArrayDeque<>();
    private final Set<CFGNode> visited = new HashSet<>();

    // Ne pas modifier
    private final Map<String, CFGNode> IF_LABEL_LINK = new HashMap<>();

    public AvailableExpressionVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, null);

        fillSuccessors();

        computeAvailableExpr();
        eliminateCommonExpression();

        computeAvailableExpr();

        printCode();
        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            RETURNS.add(((ASTIdentifier) node.jjtGetChild(i)).getValue());
        }
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        Set<CFGNode> predecessors = new HashSet<>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            predecessors = (Set<CFGNode>) node.jjtGetChild(i).jjtAccept(this, predecessors);
        }
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTIfStmt node, Object data) {
        String cond = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String label = node.getValue();

        Set<CFGNode> pred = data != null ? (Set<CFGNode>) data : new HashSet<>();
        CFGNode cfgNode = new CFGNode("if", "", cond, label, pred);
        IF_LABEL_LINK.put(label, cfgNode);
        CFG.add(cfgNode);

        pred.clear();
        pred.add(cfgNode);

        return pred;
    }

    @Override
    public Object visit(ASTLabelStmt node, Object data) {
        Set<CFGNode> pred = data != null ? (Set<CFGNode>) data : new HashSet<>();
        pred.add(IF_LABEL_LINK.get(node.getValue()));

        return pred;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String right = (String) node.jjtGetChild(2).jjtAccept(this, null);
        String op = node.getOp();

        Set<CFGNode> pred = data != null ? (Set<CFGNode>) data : new HashSet<>();

        CFGNode cfgNode = new CFGNode(op, assign, left, right, pred);
        pred.clear();
        pred.add(cfgNode);
        CFG.add(cfgNode);

        return pred;
    }

    @Override
    public Object visit(ASTAssignUnaryStmt node, Object data) {
        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);

        Set<CFGNode> pred = data != null ? (Set<CFGNode>) data : new HashSet<>();

        CFGNode cfgNode = new CFGNode("-", assign, left, "", pred);
        pred.clear();
        pred.add(cfgNode);
        CFG.add(cfgNode);

        return pred;
    }

    @Override
    public Object visit(ASTAssignDirectStmt node, Object data) {
        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);

        Set<CFGNode> pred = data != null ? (Set<CFGNode>) data : new HashSet<>();

        CFGNode cfgNode = new CFGNode("+", assign, left, "", pred);
        pred.clear();
        pred.add(cfgNode);
        CFG.add(cfgNode);

        return pred;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, null);
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return "#" + node.getValue();
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        return node.getValue();
    }

    private void fillSuccessors() {
        for (CFGNode node : CFG) {
            for (CFGNode pred : node.PREDECESSORS) {
                pred.SUCCESSORS.add(node);
            }
        }
    }

    /* ======= Début: Section à modifier ========= */

    /**
     * Computes the GEN sets for each line of code.
     */
    public void computeGenSets() {
        // TODO
        for (CFGNode node : CFG) {
            node.GEN.clear();
            if (node.isIfStmt() || node.right.isEmpty()) {
                continue;
            }

            if (node.left.equals(node.ASSIGN) || node.right.equals(node.ASSIGN)) {
                continue;
            }
            node.GEN.add(new Expression(node.left, node.op, node.right));
        }
    }

    /**
     * Computes the KILL sets for each line of code.
     */
    public void computeKillSets() {
        // TODO
        for (CFGNode node : CFG) {
            node.KILL.clear();
            if (node.isIfStmt()) {
                continue;
            }

            for (CFGNode otherNode : CFG) {
                if (!otherNode.isIfStmt()
                        && !otherNode.right.isEmpty()
                        && (otherNode.left.equals(node.ASSIGN) || otherNode.right.equals(node.ASSIGN))) {
                    node.KILL.add(new Expression(otherNode.left, otherNode.op, otherNode.right));
                }
            }
        }
    }

    /**
     * Computes the Available Expression Analysis for the code.
     */
    private void computeAvailableExpr() {
        computeGenSets();
        computeKillSets();

        // TODO
        visited.clear();
        worklist.clear();

        if (CFG.isEmpty()) {
            return;
        }

        // U = ensemble de toutes les expressions du programme
        Set<Expression> universe = new HashSet<>();
        for (CFGNode node : CFG) {
            universe.addAll(node.GEN);
        }

        // IN[node] = U, OUT[node] = U
        for (CFGNode node : CFG) {
            node.Avail_IN = new HashSet<>(universe);
            node.Avail_OUT = new HashSet<>(universe);
        }

        // IN[entry] = ∅
        CFGNode entryNode = CFG.get(0);
        entryNode.Avail_IN = new HashSet<>();
        visited.add(entryNode);
        worklist.addLast(entryNode);

        while (!worklist.isEmpty()) {
            CFGNode node = worklist.removeFirst();

            // OUT[node] = GEN[node] ∪ (IN[node] - KILL[node])
            Set<Expression> out = new HashSet<>(node.Avail_IN);
            out.removeAll(node.KILL);
            out.addAll(node.GEN);
            node.Avail_OUT = out;

            for (CFGNode succNode : node.SUCCESSORS) {
                // propagateFlag = (IN[succNode] ⊄ OUT[node]) ∨ ¬visited.contains(succ)
                boolean propagate = !node.Avail_OUT.containsAll(succNode.Avail_IN)
                        || !visited.contains(succNode);
                if (propagate) {
                    // IN[succNode] = IN[succNode] ∩ OUT[node]
                    succNode.Avail_IN.retainAll(node.Avail_OUT);
                    worklist.addLast(succNode);
                    visited.add(succNode);
                }
            }
        }
    }

    /**
     * Eliminates common expressions in the code using the Available Expression Analysis.
     */
    private void eliminateCommonExpression() {
        // TODO
    }

    /* ======= Fin: Section à modifier ========= */

    public void printCode() {
        int i = 0;
        for (CFGNode code : CFG) {
            String line;
            if (code.isIfStmt()) {
                line = "if " + code.left + " goto " + code.right;
            } else {
                line = code.ASSIGN + " = " + code.left;
                if (!code.right.isEmpty() && !code.right.equals("#0")) {
                    line += " " + code.op + " " + code.right;
                }
            }
            m_writer.println("// Bloc " + i);
            m_writer.println(line);
            m_writer.println("// Avail_IN  : " + sortedExpressions(code.Avail_IN));
            m_writer.println("// Avail_OUT : " + sortedExpressions(code.Avail_OUT));
            m_writer.println();
            i++;
        }
    }


    // Helper function to convert a set of Expression objects to a sorted list of strings
    private List<String> sortedExpressions(Set<Expression> expressions) {
        return expressions.stream()
                .map(Expression::toString)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * A struct to store the data of a code line.
     */
    public class CFGNode {
        public String op;
        public String ASSIGN;
        public String left;
        public String right;
        public Set<Expression> GEN;
        public Set<Expression> KILL;
        public Set<Expression> Avail_IN;
        public Set<Expression> Avail_OUT;
        public Set<CFGNode> PREDECESSORS;
        public Set<CFGNode> SUCCESSORS;

        public CFGNode(String op, String ASSIGN, String left, String right, Set<CFGNode> predecessors) {
            this.op = op;
            this.ASSIGN = ASSIGN;
            this.left = left;
            this.right = right;
            this.GEN = new HashSet<>();
            this.KILL = new HashSet<>();
            this.Avail_IN = new HashSet<>();
            this.Avail_OUT = new HashSet<>();
            this.PREDECESSORS = (Set<CFGNode>) ((HashSet<CFGNode>) predecessors).clone();
            this.SUCCESSORS = new HashSet<>();
        }

        public boolean isIfStmt() {
            return this.op.equals("if");
        }

        @Override
        public String toString() {
            return "CFGNode{" +
                    "op='" + op + '\'' +
                    ", ASSIGN='" + ASSIGN + '\'' +
                    ", left='" + left + '\'' +
                    ", right='" + right + '\'' +
                    ", GEN=" + GEN +
                    ", KILL=" + KILL +
                    ", Avail_IN=" + Avail_IN +
                    ", Avail_OUT=" + Avail_OUT +
                    ", PREDECESSORS=" + PREDECESSORS.size() +
                    ", SUCCESSORS=" + SUCCESSORS.size() +
                    '}';
        }
    }

    /**
     * A struct to store an arithmetic expression.
     */
    public static class Expression {
        String left;
        String op;
        String right;

        public Expression(String left, String op, String right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Expression that = (Expression) obj;
            return Objects.equals(left, that.left) &&
                    Objects.equals(op, that.op) &&
                    Objects.equals(right, that.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, op, right);
        }

        @Override
        public String toString() {
            return left + op + right;
        }
    }
}