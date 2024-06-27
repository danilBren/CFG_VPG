package grammar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.misc.Pair;

public class NonTerminal extends Node {

    public Set<ArrayList<Pair<Node, EbnfSuffix>>> rules;

    public NonTerminal(String label) {
        super(label);
        rules = new HashSet<>();
    }

    public void addRule(ArrayList<Pair<Node, EbnfSuffix>> rule) {
        rules.add(rule);
    }

    public void setRules(Set<ArrayList<Pair<Node, EbnfSuffix>>> rules) {
        this.rules = rules;
    }

    public String printFullRule() {
        StringBuilder str = new StringBuilder();
        str.append(super.getName());
        boolean fst = true;
        for (ArrayList<Pair<Node, EbnfSuffix>> alternative : rules) {
            if (fst) { // to have ':' at the 1st rule like in ANTLR
                str.append("\t:");
                fst = false;
            } else {
                str.append("\n\t|");
            }
            for (Pair<Node, EbnfSuffix> pair : alternative) {
                str.append(" ").append(pair.a.getName());
                switch (pair.b) {
                    case QUESTION -> str.append("?");
                    case STAR -> str.append("*");
                    case PLUS -> str.append("+");
                }
            }
        }
        str.append("\n\t;");
        return str.toString();
    }

    public String grammarToString() {
        return grammarToString(new HashSet<>());
    }

    private String grammarToString(Set<NonTerminal> printed) {
        StringBuilder res = new StringBuilder(this.printFullRule());
        printed.add(this);
        for (var alt : rules) {
            for (var pair : alt) {
                if (pair.a instanceof NonTerminal && !(printed.contains(pair.a))) {
                    printed.add((NonTerminal) pair.a);
                    res.append("\n\n").append(((NonTerminal) pair.a).grammarToString(printed));
                }
            }
        }
        return res.toString();
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
