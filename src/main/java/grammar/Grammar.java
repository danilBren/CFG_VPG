package grammar;

import java.util.*;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.misc.Pair;

public class Grammar {

    public final ArrayList<Pair<Terminal, Terminal>> pairs;
    public Set<Terminal> call;
    public Set<Terminal> ret;

    //set of terminals that have an EBNFSuffix
    private final Set<Terminal> onlyPlain;
    public final NonTerminal start;
    private final Set<NonTerminal> nonTerminals;
    private final Set<Terminal> terminals;


    public Grammar(NonTerminal start) {
        pairs = new ArrayList<>();
        onlyPlain = new HashSet<>();
        this.start = start;
        nonTerminals = new HashSet<>();
        terminals = new HashSet<>();
        call = new HashSet<>();
        ret = new HashSet<>();
        getNodes();
        makePairs();
    }

    public int getNotermCount() {
        return nonTerminals.size();
    }

    public Grammar(NonTerminal start, Set<NonTerminal> nonTerminals, Set<Terminal> terminals) {
        pairs = new ArrayList<>();
        onlyPlain = new HashSet<>();
        this.start = start;
        this.nonTerminals = nonTerminals;
        this.terminals = terminals;
        makePairs();
    }

    /**
     * To get the set of terminals and nonterminals if it wasn't given to the constructor.
     */
    private void getNodes() {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(start);
        visited.add(start);
        while (!toVisit.isEmpty()) {
            NonTerminal node = toVisit.pop();
            for (var alt : node.rules) {
                for (var p : alt) {
                    if (p.a instanceof NonTerminal) {
                        this.nonTerminals.add((NonTerminal) p.a);
                        if (!visited.contains(p.a)) {
                            visited.add((NonTerminal) p.a);
                            toVisit.add((NonTerminal) p.a);
                        }
                    } else {
                        this.terminals.add((Terminal) p.a);
                    }
                }
            }
        }
    }

    private Set<NonTerminal> tagAlternative(ArrayList<Pair<Node, EbnfSuffix>> alt) {
        Set<Terminal> paired = new HashSet<>();
        Set<NonTerminal> newNonTerms = new HashSet<>();
        boolean foundPair = false;
        for (int i = 0; i < alt.size(); i++) {
            var c = alt.get(i);
            if (c.a instanceof Terminal) {
                // check if there are other terminals in the rule
                foundPair = false;
                if (c.b == EbnfSuffix.NONE) {
                    int j = alt.size() - 1;
                    while (j > i) {
                        var r = alt.get(j);
                        j--;
                        if (r.a instanceof Terminal && r.b == EbnfSuffix.NONE && !c.a.equals(r.a)) {
                            pairs.add(new Pair<>((Terminal) c.a, (Terminal) r.a));
                            foundPair = true;
                            paired.add((Terminal) c.a);
                            paired.add((Terminal) r.a);
                        }
                    }
                } else {
                    onlyPlain.add((Terminal) c.a);
                }
                if (!foundPair && !paired.contains(c.a)) {
                    onlyPlain.add((Terminal) c.a);
                }
            } else {
                newNonTerms.add((NonTerminal) c.a);
            }
        }
        return newNonTerms;
    }

    private Set<NonTerminal> tagRule(Set<ArrayList<Pair<Node, EbnfSuffix>>> rule) {
        Set<NonTerminal> newNonTerms = new HashSet<>();
        for (var alt : rule) {
            newNonTerms.addAll(tagAlternative(alt));
        }
        return newNonTerms;
    }

    /**
     * counts pairs and puts them in countedPairs map.
     */
    private Map<Pair<Terminal, Terminal>, Long> countPairs() {
        return pairs.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
    }

    /**
     * @param removeContradictions if true, pairs that were not added because of contradictions,
     *                             will be removed from sortedPairs map
     */
    public void tagByPrecedence(boolean removeContradictions) {
        call = new HashSet<>();
        ret = new HashSet<>();
        var counts = countPairs().entrySet().stream().sorted(Map.Entry.comparingByValue());

        // stack with pairs sorted such that most frequent pairs are on top.
        Stack<Pair<Terminal, Terminal>> pairsStack = new Stack<>();
        counts.forEach(entry -> pairsStack.push(entry.getKey()));
        while (!pairsStack.isEmpty()) {
            var pair = pairsStack.pop();
            if (pair.a.equals(pair.b) || onlyPlain.contains(pair.a) || onlyPlain.contains(pair.b) ||
                    call.contains(pair.a) || ret.contains(pair.a) || call.contains(pair.b) ||
                    ret.contains(pair.b)) {
                if (removeContradictions) {
                    pairs.removeAll(Collections.singleton(pair));
                }
            } else {
                call.add(pair.a);
                ret.add(pair.b);
            }
        }
    }

    /**
     * if a terminal is in the call set, but there is a rule where it is not matched with a return,
     * remove it from the call set. Same for the return set.
     */
    public void removeNonMatchingTagging() {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(start);
        while (!toVisit.isEmpty()) {
            var nt = toVisit.pop();
            for (var alts : nt.rules) {
                Map<Terminal, Integer> counts = new HashMap<>();
                for (var e : alts) {
                    if (e.a instanceof Terminal) {
                        if (e.b == EbnfSuffix.NONE) {
                            counts.merge((Terminal) e.a, 1, Integer::sum);
                        }
                    } else {
                        if (!visited.contains((NonTerminal) e.a)) {
                            visited.add((NonTerminal) e.a);
                            toVisit.push((NonTerminal) e.a);
                        }
                    }
                }
                counts.forEach((t, c) -> {
                    if (call.contains(t)) {
                        if (c > counts.getOrDefault(findPair(t, true), 0)) {
                            onlyPlain.add(t);
                        }
                    } else if (ret.contains(t)) {
                        if (c > counts.getOrDefault(findPair(t, false), 0)) {
                            onlyPlain.add(t);
                        }
                    }
                });
            }
        }
    }

    /**
     * Converts rule to a simple form by making sure there is only one NonTerminal symbol between a
     * call - return pair.
     */
    public void convertToSimpleForm() {
        if (call.isEmpty()) {
            // it does not make sense to convert it with no tagging
            return;
        }
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(start);
        visited.add(start);
        while (!toVisit.isEmpty()) {
            var nt = toVisit.pop();
            for (var alt : nt.rules) {
                // find start of a matched token
                for (int i = 0; i < alt.size(); i++) {
                    if (alt.get(i).a instanceof NonTerminal && !visited.contains(alt.get(i).a)) {
                        visited.add((NonTerminal) alt.get(i).a);
                        toVisit.push((NonTerminal) alt.get(i).a);
                    }
                    if (call.contains(alt.get(i).a)) {
                        // find end of a matched token
                        // accept it only if there are 2 or more symbols in between
                        for (int j = i; j < alt.size(); j++) {
                            if (j - i > 2 && ret.contains(alt.get(j).a) &&
                                    findPair((Terminal) alt.get(i).a, true).equals(alt.get(j).a)) {
                                String name = "_L";
                                ArrayList<Pair<Node, EbnfSuffix>> rule = new ArrayList<>();
                                for (int k = i + 1; k < j; k++) {
                                    name += "_" + alt.get(k).a.getName() +
                                            (alt.get(k).b.equals(EbnfSuffix.NONE) ? "" :
                                                    "^" + alt.get(k).b.toString());
                                    rule.add(alt.get(k));
                                }
                                for (int k = i + 1; k < j; k++) {
                                    alt.remove(i + 1);
                                }
                                NonTerminal newNonTerm = new NonTerminal(name);
                                alt.add(i + 1, new Pair(newNonTerm, EbnfSuffix.NONE));
                                newNonTerm.addRule(rule);
                                nonTerminals.add(newNonTerm);
                            }
                        }
                    }
                }
            }
        }
    }

    private Terminal findPair(Terminal t, boolean isCall) {
        if (isCall) {
            for (var pair : pairs) {
                if (pair.a.equals(t)) {
                    return pair.b;
                }
            }
        } else {
            for (var pair : pairs) {
                if (pair.b.equals(t)) {
                    return pair.a;
                }
            }
        }
        return null;
    }

    public boolean isLeftRecursive() {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(start);
        visited.add(start);
        while (!toVisit.isEmpty()) {
            NonTerminal current = toVisit.pop();
            for (var alt : current.rules) {
                // if a rule is left recursive
                if (!alt.isEmpty() && alt.get(0).a.equals(current)) {
                    return true;
                }
                alt.stream().filter(e -> e.a instanceof NonTerminal).forEach(e -> {
                    if (!visited.contains(e.a)) {
                        toVisit.add((NonTerminal) e.a);
                        visited.add((NonTerminal) e.a);
                    }
                });
            }
        }
        return false;
    }

    public void makePairs() {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> tagged = new HashSet<>();
        toVisit.push(start);
        tagged.add(start);
        while (!toVisit.isEmpty()) {
            var g = toVisit.pop();
            // returns a set of new nonTerminals encountered in this rule.
            var newRules = tagRule(g.rules);
            for (var nt : newRules) {
                if (!tagged.contains(nt)) {
                    tagged.add(nt);
                    toVisit.add(nt);
                }
            }
        }
    }

    /**
     * This method returns tagging instead of saving it to the field
     * because it is only made for testing.
     *
     * @return a pair with call-return set
     */
    public Pair<Set<Terminal>, Set<Terminal>> bruteForceTagging() {
        HashSet<Terminal> brCall = new HashSet<>();
        HashSet<Terminal> brRet = new HashSet<>();

        for (var c : terminals) {
            for (var r : terminals) {
                if (!c.equals(r) && !brRet.contains(c) && !brCall.contains(r)) {
                    var nc = new HashSet<>(brCall);
                    nc.add(c);
                    var nr = new HashSet<>(brRet);
                    nr.add(r);
                    if (Helpers.isWellMatched(start, nc, nr)) {
                        brCall.add(c);
                        brRet.add(r);
                    }
                }
            }
        }
        return new Pair<>(brCall, brRet);
    }

    /*
     https://www.geeksforgeeks.org/removing-direct-and-indirect-left-recursion-in-a-grammar/
    */
    private NonTerminal removeImmediateLR(NonTerminal nt) {
        Set<ArrayList<Pair<Node, EbnfSuffix>>> alphas = new HashSet<>();
        Set<ArrayList<Pair<Node, EbnfSuffix>>> betas = new HashSet<>();

        NonTerminal newNonTerm = new NonTerminal(nt.getName() + "`");
        Set<ArrayList<Pair<Node, EbnfSuffix>>> newRulesA = new HashSet<>();
        Set<ArrayList<Pair<Node, EbnfSuffix>>> newRulesA1 = new HashSet<>();

        for (var rule : nt.rules) {
            if (!rule.isEmpty() && rule.get(0).a.equals(nt)) {
                alphas.add(new ArrayList<>(rule.subList(1, rule.size())));
            } else {
                betas.add(rule);
            }
        }

        if (alphas.isEmpty()) {
            return null;
        }

        if (betas.isEmpty()) {
            ArrayList<Pair<Node, EbnfSuffix>> toAdd = new ArrayList<>();
            toAdd.add(new Pair<>(newNonTerm, EbnfSuffix.NONE));
            newRulesA.add(toAdd);
        }

        for (var beta : betas) {
            beta.add(new Pair<>(newNonTerm, EbnfSuffix.NONE));
            newRulesA.add(beta);
        }

        for (var alpha : alphas) {
            alpha.add(new Pair<>(newNonTerm, EbnfSuffix.NONE));
            newRulesA1.add(alpha);
        }

        nt.setRules(newRulesA);
        newRulesA1.add(new ArrayList<>());
        newNonTerm.setRules(newRulesA1);
        return newNonTerm;
    }

    public void removeLR() {
        Set<NonTerminal> toAdd = new HashSet<>();
        for (var nt : nonTerminals) {
            var newNonTerm = removeImmediateLR(nt);
            if (newNonTerm != null) {
                toAdd.add(newNonTerm);
            }
        }
        nonTerminals.addAll(toAdd);
    }

    public String printTaggedGrammar() {
        String res = "";
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(start);
        visited.add(start);
        while (!toVisit.isEmpty()) {
            res += "\n\n";
            NonTerminal current = toVisit.pop();
            res += printTaggedRule(current);
            for (var rule : current.rules) {
                for (var n : rule) {
                    if (n.a instanceof NonTerminal && !visited.contains(n.a)) {
                        visited.add((NonTerminal) n.a);
                        toVisit.push((NonTerminal) n.a);
                    }
                }
            }
        }
        return res;
    }

    private String printTaggedRule(NonTerminal nt) {
        StringBuilder res = new StringBuilder();
        res.append(nt.toString());
        boolean fst = true;
        for (ArrayList<Pair<Node, EbnfSuffix>> alternative : nt.rules) {
            if (fst) { // to have ':' at the 1st rule like in ANTLR
                res.append("\t:");
                fst = false;
            } else {
                res.append("\n\t|");
            }
            for (Pair<Node, EbnfSuffix> pair : alternative) {
                res.append(" ");
                if (call.contains(pair.a)) {
                    res.append("<");
                }
                res.append(pair.a.getName());
                if (ret.contains(pair.a)) {
                    res.append(">");
                }
                switch (pair.b) {
                    case QUESTION -> res.append("?");
                    case STAR -> res.append("*");
                    case PLUS -> res.append("+");
                }
            }
        }
        res.append("\n\t;");
        return res.toString();
    }
}