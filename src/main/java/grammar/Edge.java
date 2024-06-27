package grammar;

import java.util.ArrayList;
import java.util.List;

public class Edge {
    NonTerminal from;
    NonTerminal to;
    List<Node> s1;
    List<Node> s2;

    public Edge(NonTerminal from, NonTerminal to, List<Node> s1, List<Node> s2) {
        this.from = from;
        this.to = to;
        this.s1 = s1;
        this.s2 = s2;
    }

    @Override
    public String toString() {
        String res = "";
        res += from.getName() + " to " + to.getName();
        //        res += "\n";
        //        if (s1.size() == 0) {
        //            res += ".E.";
        //        }
        //        for (Node n : s1) {
        //            res += n.getName() + "";
        //        }
        //        res += " -> ";
        //        if (s2.size() == 0) {
        //            res += ".E.";
        //        }
        //        for (Node n : s2) {
        //            res += n.getName() + "";
        //        }
        res += "\n";
        return res;
    }
}
