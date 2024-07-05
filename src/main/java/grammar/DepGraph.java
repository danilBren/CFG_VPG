package grammar;

import java.util.*;

public class DepGraph {

    enum Flag {
        VIS, NOT_VIS, IN_STACK
    }

    public HashMap<NonTerminal, Set<Edge>> verts;
    public Set<Edge> edges;
    private Grammar grammar;
    public Set<List<NonTerminal>> cycles;
    public Set<List<Edge>> cyclesEdges;

    public DepGraph(Grammar grammar) {
        verts = new HashMap<>();
        edges = new HashSet<>();
        this.grammar = grammar;
        makeGraph();
    }

    public void makeGraph() {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(grammar.start);
        visited.add(grammar.start);
        verts.put(grammar.start, new HashSet<>());
        while (!toVisit.isEmpty()) {
            NonTerminal curr = toVisit.pop();
            var currAdj = verts.get(curr);
            for (var altEbnf : curr.rules) {
                ArrayList<Node> alt = new ArrayList<>();
                // not adding suffixes here because we are interested in what
                // a rule can produce
                altEbnf.forEach(p -> alt.add(p.a));
                for (int i = 0; i < alt.size(); i++) {
                    if (alt.get(i) instanceof NonTerminal nt) {
                        Edge newEdge = new Edge(curr, nt, alt.subList(0, i),
                                                alt.subList(i + 1, alt.size()));
                        currAdj.add(newEdge);
                        edges.add(newEdge);
                        if (!visited.contains(nt)) {
                            visited.add(nt);
                            toVisit.push(nt);
                            verts.put(nt, new HashSet<>());
                        }

                    }
                }
            }
        }
    }

    public void findCycles() {
        cycles = new HashSet<>();
        cyclesEdges = new HashSet<>();
        HashMap<NonTerminal, Flag> flags = new HashMap<>();
        verts.keySet().forEach(v -> flags.put(v, Flag.NOT_VIS));
        for (var v : verts.keySet()) {
            if (flags.get(v) == Flag.NOT_VIS) {
                Stack<NonTerminal> stack = new Stack<>();
                stack.push(v);
                Stack<Edge> edgeStack = new Stack<>();
                edgeStack.push(null);
                flags.put(v, Flag.IN_STACK);
                processDFSTree(stack, edgeStack, flags);
            }
        }
    }

    private void processDFSTree(Stack<NonTerminal> stack, Stack<Edge> edgeStack,
                                HashMap<NonTerminal, Flag> flags) {
        NonTerminal curr = stack.peek();


        for (Edge e : verts.get(curr)) {
            if (flags.get(e.to) == Flag.IN_STACK) {
                edgeStack.push(e);
                storeCycle(stack, edgeStack, e.to);
                // remove it to completely backtrack
                edgeStack.pop();
            } else if (flags.get(e.to) == Flag.NOT_VIS) {
                stack.push(e.to);
                edgeStack.push(e);
                flags.put(e.to, Flag.IN_STACK);
                processDFSTree(stack, edgeStack, flags);
            }
        }
        flags.put(curr, Flag.NOT_VIS);
        stack.pop();
        edgeStack.pop();
    }

    private void storeCycle(Stack<NonTerminal> st, Stack<Edge> es, NonTerminal v) {
        Stack<NonTerminal> stack = (Stack<NonTerminal>) st.clone();
        Stack<Edge> edgeStack = (Stack<Edge>) es.clone();
        ArrayList<NonTerminal> newCycle = new ArrayList<>();
        ArrayList<Edge> newCycleEdges = new ArrayList<>();
        do {
            newCycle.add(stack.pop());
            newCycleEdges.add(edgeStack.pop());
        } while (!newCycle.get(newCycle.size() - 1).equals(v));

        Collections.reverse(newCycle);
        if (!cycles.contains(newCycle)) {
            cycles.add(newCycle);
        }

        Collections.reverse(newCycleEdges);
        if (!cyclesEdges.contains(newCycleEdges)) {
            cyclesEdges.add(newCycleEdges);
        }
    }

    public boolean isValid() {
        for (var c : cyclesEdges) {
            if (!isCycleValid(c)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCycleValid(List<Edge> cycle) {
        // check the 1st case

        for (var e : cycle) {
            if (e.s1.isEmpty() || e.s2.isEmpty()) {
                continue;
            }
            if (grammar.call.contains(e.s1.get(e.s1.size() - 1))) {
                if (grammar.ret.contains(e.s2.get(e.s2.size() - 1))) {
                    // 1st case: an edge is labeled
                    // s1<aLb>s2
                    return true;
                }
            }
        }

        //2nd case
        for (var e : cycle) {
            if (!e.s2.isEmpty()) {
                return false;
            }
        }
        for (var e : cycle) {
            for (var n : e.s1) {
                if (n instanceof Terminal || canDeriveNonemptyString((NonTerminal) n)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canDeriveNonemptyString(NonTerminal nt) {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(nt);
        visited.add(nt);
        while (!toVisit.isEmpty()) {
            NonTerminal cur = toVisit.pop();
            for (var alt : cur.rules) {
                for (var e : alt) {
                    if (e.a instanceof Terminal) {
                        return true;
                    }
                    var newNt = (NonTerminal) e.a;
                    if (!visited.contains(newNt)) {
                        toVisit.push(newNt);
                        visited.add(newNt);
                    }
                }
            }
        }
        return false;
    }


}
