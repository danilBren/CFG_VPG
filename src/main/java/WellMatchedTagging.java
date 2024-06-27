import grammar.EbnfSuffix;
import grammar.Node;
import grammar.NonTerminal;
import grammar.Terminal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import org.antlr.v4.runtime.misc.Pair;

public class WellMatchedTagging {

    static Pair<Set<Terminal>, Set<Terminal>> tagGrammar(NonTerminal start) {
        Set<Terminal> call = new HashSet<>();
        Set<Terminal> ret = new HashSet<>();
        Set<NonTerminal> tagged = new HashSet<>();
        Stack<NonTerminal> toTag = new Stack<>();

        toTag.add(start);
        tagged.add(start);

        while (!toTag.isEmpty()) {
            var nt = toTag.pop();
            tagged.add(nt);
            for (var alt : nt.rules) {
                for (int i = 0; i < alt.size(); i++) {
                    var pc = alt.get(i);
                    if (pc.a instanceof Terminal && pc.b == EbnfSuffix.NONE &&
                            !ret.contains(pc.a)) {
                        int j = alt.size() - 1;
                        while (j > i) {
                            var pr = alt.get(j);
                            j--;
                            if (pr.a instanceof NonTerminal) {
                                if (!toTag.contains(pr.a) && !tagged.contains(pr.a)) {
                                    toTag.push((NonTerminal) pr.a);
                                }
                            } else if (pr.a instanceof Terminal && pr.b == EbnfSuffix.NONE &&
                                    !call.contains(pr.a) && findNonTerm(alt, i, j) &&
                                    !pc.a.getName().equals(pc.b.name())) {
                                call.add((Terminal) pc.a);
                                ret.add((Terminal) pr.a);
                                break;
                            }
                        }
                    } else if (pc.a instanceof NonTerminal && !toTag.contains(pc.a) &&
                            !tagged.contains(pc.a)) {
                        toTag.push((NonTerminal) pc.a);
                    }
                }
            }
        }
        return new Pair<>(call, ret);
    }

    /**
     * @param rule list representing a grammar rule.
     * @param i    start, not included
     * @param j    end, not included
     * @return true if there's a nonterminal between i and j, false otherwise.
     */
    static boolean findNonTerm(ArrayList<Pair<Node, EbnfSuffix>> rule, int i, int j) {
        for (int k = i + 1; k < j; k++) {
            if (rule.get(k).a instanceof NonTerminal) {
                return true;
            }
        }
        return false;
    }

}
