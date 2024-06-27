package grammar;

import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class Helpers {

    public static boolean rulesEqual(NonTerminal nt1, NonTerminal nt2) {
        return nt1.rules.equals(nt2.rules);
    }

    public static boolean isWellMatched(NonTerminal start, Set<Terminal> testCall,
                                        Set<Terminal> testRet) {
        Stack<NonTerminal> toVisit = new Stack<>();
        Set<NonTerminal> visited = new HashSet<>();
        toVisit.push(start);
        visited.add(start);
        while (!toVisit.isEmpty()) {
            var nt = toVisit.pop();
            for (var alt : nt.rules) {
                Stack<Terminal> stack = new Stack<>();
                for (var e : alt) {
                    if (e.a instanceof Terminal) {
                        if (testCall.contains(e.a) && testRet.contains(e.a) &&
                                e.b != EbnfSuffix.NONE) {
                            return false;
                        }
                        if (testCall.contains(e.a)) {
                            stack.push((Terminal) e.a);
                        } else if (testRet.contains(e.a)) {
                            try {
                                stack.pop();
                                if (alt.indexOf(e.a) - 1 == alt.indexOf(e.a)) {
                                    return false;
                                }
                            } catch (EmptyStackException ignored) {
                                return false;
                            }
                        }
                    } else if (!visited.contains((NonTerminal) e.a)) {
                        toVisit.add((NonTerminal) e.a);
                        visited.add((NonTerminal) e.a);
                    }
                }
                if (!stack.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
