import grammar.Exceptions.ActionException;
import grammar.EbnfSuffix;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

public class MyVisitor extends ANTLRv4ParserBaseVisitor<ArrayList<Pair<String, EbnfSuffix>>> {

    ParseTreeProperty<String> ptp = new ParseTreeProperty<>();

    private final HashMap<String, List<List<Pair<String, EbnfSuffix>>>> rules = new HashMap<>();

    private final Set<String> terminals = new HashSet<>();

    private final Set<String> nonTerminals = new HashSet<>();

    HashMap<String, String> newRuleSet = new HashMap<>();

    private String start = null;

    int newRuleCount = 0;

    /*
        parserRuleSpec
        : ruleModifiers? RULE_REF argActionBlock? ruleReturns? throwsSpec? localsSpec? rulePrequel* COLON ruleBlock SEMI
            exceptionGroup
        ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitParserRuleSpec(
            ANTLRv4Parser.ParserRuleSpecContext ctx) {
        String rule = ctx.RULE_REF().getSymbol().getText();
        if (!rules.containsKey(ctx.RULE_REF().getSymbol().getText())) {
            rules.put(rule, new ArrayList<>());
            nonTerminals.add(rule);
            ptp.put(ctx.ruleBlock(), rule);
            if (start == null) {
                start = rule;
            }
            var ruleBlockRes = ctx.ruleBlock().accept(this);
        } else {
            Converter.logger.log(Level.SEVERE, "Potential duplicate keys");
        }
        return null;
    }

    /*
    ruleAltList
    : labeledAlt (OR labeledAlt)*
    ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitRuleAltList(
            ANTLRv4Parser.RuleAltListContext ctx) {
        ArrayList<Pair<String, EbnfSuffix>> result = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i += 2) {
            var childResult = ctx.getChild(i).accept(this);
            rules.get(ptp.get(ctx.parent)).add(childResult);
        }
        return result;
    }

    /*
    alternative
    : elementOptions? element+
    |
    // explicitly allow empty alts
    ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitAlternative(
            ANTLRv4Parser.AlternativeContext ctx) {
        ArrayList<Pair<String, EbnfSuffix>> result = new ArrayList<>();
        for (int i = 0; i < ctx.element().size(); i++) {
            var childResult = ctx.element(i).accept(this);
            result = this.aggregateResult(result, childResult);
        }
        return result;
    }

    /*
    element
    : labeledElement (ebnfSuffix |)
    | atom (ebnfSuffix |)
    | ebnf
    | actionBlock (QUESTION predicateOptions?)?
    ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitElement(ANTLRv4Parser.ElementContext ctx)
            throws ActionException {
        ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
        if (ctx.labeledElement() != null) {
            // didn't see that yet
            //            System.out.println("LABELLED ELEMENT\t" + ctx.labeledElement().getText() + "\n In " +
            //                                       ctx.getText());
            var lElement = ctx.labeledElement().accept(this);
            if (ctx.ebnfSuffix() != null) {
                var suffix = ctx.ebnfSuffix().accept(this);
                res.add(combinePair(lElement.get(0), suffix.get(0)));
            } else {
                res.add(lElement.get(0));
            }
            return res;
        } else if (ctx.atom() != null) {
            // just a terminal or nonterminal
            var atom = ctx.atom().accept(this);
            if (ctx.ebnfSuffix() != null) {
                var suffix = ctx.ebnfSuffix().accept(this);
                res.add(combinePair(atom.get(0), suffix.get(0)));
            } else {
                res.add(atom.get(0));
            }
            return res;
        } else if (ctx.ebnf() != null) {
            // stuff in parentheses
            return ctx.ebnf().accept(this);
        } else if (ctx.actionBlock() != null) {
            throw new ActionException(
                    "ACTION BLOCK\t" + ctx.actionBlock().getText() + "\n In " + ctx.getText());
        }
        return null;
    }

    /*
    labeledElement
    : identifier (ASSIGN | PLUS_ASSIGN) (atom | block)
    ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitLabeledElement(
            ANTLRv4Parser.LabeledElementContext ctx) {
        if (ctx.atom() != null) {
            return ctx.atom().accept(this);
        } else {
            return ctx.block().accept(this);
        }
    }

    /*
        atom
        : terminalDef
        | ruleref
        | notSet
        | DOT elementOptions?
        ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitAtom(ANTLRv4Parser.AtomContext ctx) {
        if (ctx.terminalDef() != null) {
            // visit a terminal
            var terminal = ctx.terminalDef().accept(this);
            terminals.add(terminal.get(0).a);
            return terminal;
        } else if (ctx.ruleref() != null) {
            // visit a noneterminal ref
            ArrayList<Pair<String, EbnfSuffix>> nonTerm = ctx.ruleref().accept(this);
            nonTerminals.add(nonTerm.get(0).a);
            return nonTerm;
        } else {
            Pair<String, EbnfSuffix> notSet = new Pair<>(ctx.getText(), EbnfSuffix.NONE);
            ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
            res.add(notSet);
            nonTerminals.add(notSet.a);
            //            System.out.println(
            //                    "a notSet or elementOptions  " + ctx.getText() + "\n");
            return res;
        }
    }

    /*
    ruleref
    : RULE_REF argActionBlock? elementOptions?
    ;    */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitRuleref(ANTLRv4Parser.RulerefContext ctx) {
        if (ctx.argActionBlock() != null || ctx.argActionBlock() != null) {
            System.out.println(
                    "WARNING: " + ctx.getText() + " has an argActionBlock or elementOptions");
        }
        nonTerminals.add(ctx.RULE_REF().getText());
        ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
        res.add(new Pair<>(ctx.RULE_REF().getText(), EbnfSuffix.NONE));
        return res;
    }

    /*
    ebnf
    : block blockSuffix?
    ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitEbnf(ANTLRv4Parser.EbnfContext ctx) {
        ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
        ArrayList<Pair<String, EbnfSuffix>> blockRes = ctx.block().accept(this);
        if (ctx.blockSuffix() != null) {
            var suffix = ctx.blockSuffix().accept(this);
            res.add(new Pair<>(blockRes.get(0).a, suffix.get(0).b));
        } else {
            res.add(blockRes.get(0));
        }
        return res;
    }

    /*
    block
    : LPAREN (optionsSpec? ruleAction* COLON)? altList RPAREN
    ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitBlock(ANTLRv4Parser.BlockContext ctx) {
        ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
        if (newRuleSet.containsKey(ctx.getText())) {
            String rule = newRuleSet.get(ctx.getText());
            ptp.put(ctx, rule);
            res.add(new Pair<>(rule, EbnfSuffix.NONE));
        } else {
            newRuleCount++;
            String rule = "_new_rule_" + newRuleCount;
            newRuleSet.put(ctx.getText(), rule);
            nonTerminals.add(rule);
            rules.put(rule, new ArrayList<>());
            ptp.put(ctx, rule);
            res.add(new Pair<>(rule, EbnfSuffix.NONE));
        }
        super.visitBlock(ctx);
        return res;
    }

    /*
        altList
        : alternative (OR alternative)*
        ;   */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitAltList(ANTLRv4Parser.AltListContext ctx) {
        ArrayList<Pair<String, EbnfSuffix>> result = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i += 2) {
            var childResult = ctx.getChild(i).accept(this);
            rules.get(ptp.get(ctx.parent)).add(childResult);
        }
        return result;
    }

    /*
    terminalDef
    : TOKEN_REF elementOptions?
    | STRING_LITERAL elementOptions?
    ;     */
    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitTerminalDef(
            ANTLRv4Parser.TerminalDefContext ctx) {
        if (ctx.elementOptions() != null) {
            Converter.logger.log(Level.WARNING, "TerminalDef " + ctx.getText() + " has elementOptions " +
                                       ctx.elementOptions().getText());
        }
        ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
        if (ctx.TOKEN_REF() != null) {
            res.add(new Pair<>(ctx.TOKEN_REF().getText(), EbnfSuffix.NONE));
        } else {
            res.add(new Pair<>(ctx.STRING_LITERAL().getText(), EbnfSuffix.NONE));
        }
        return res;
    }

    @Override
    public ArrayList<Pair<String, EbnfSuffix>> visitEbnfSuffix(
            ANTLRv4Parser.EbnfSuffixContext ctx) {
        if (ctx.getChildCount() == 2) {
            //System.out.println(ctx.parent.getText() + "\nThere's another question and I am not sure what that means");
        }
        ArrayList<Pair<String, EbnfSuffix>> res = new ArrayList<>();
        switch (ctx.getChild(0).getText()) {
            case "?":
                res.add(new Pair<>(null, EbnfSuffix.QUESTION));
                break;
            case "*":
                res.add(new Pair<>(null, EbnfSuffix.STAR));
                break;
            case "+":
                res.add(new Pair<>(null, EbnfSuffix.PLUS));
                break;
        }
        return res;
    }

    @Override
    protected ArrayList<Pair<String, EbnfSuffix>> aggregateResult(
            ArrayList<Pair<String, EbnfSuffix>> aggregate,
            ArrayList<Pair<String, EbnfSuffix>> nextResult) {
        if (aggregate == null) {
            return nextResult;
        } else if (nextResult == null) {
            return aggregate;
        }
        return new ArrayList<>(Stream.concat(aggregate.stream(), nextResult.stream()).toList());
    }

    public HashMap<String, List<List<Pair<String, EbnfSuffix>>>> getRules() {
        return rules;
    }

    public Set<String> getTerminals() {
        return terminals;
    }

    public Set<String> getNonTerminals() {
        return nonTerminals;
    }

    private Pair<String, EbnfSuffix> combinePair(Pair<String, EbnfSuffix> p1,
                                                 Pair<String, EbnfSuffix> p2) {
        return new Pair<>(p1.a, p2.b);
    }

    public String getStart() {
        return start;
    }
}
