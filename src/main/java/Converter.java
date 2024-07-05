import grammar.Exceptions.ActionException;
import grammar.Exceptions.NoRulesException;
import grammar.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.io.FileUtils;

import static java.util.stream.Collectors.toMap;

public class Converter {

    public static final boolean DO_BRUTEFORCE = false;

    public static int taggedGrammars = 0;
    public static int lrCount = 0;
    public static int lrInVPGsCount = 0;
    public static int diffWithBruteforce = 0;
    public static String grammarsWithLR = "";
    public static String differentGrammars = "";
    public static final String RES_PATH = "results";
    public static int validGrammars = 0;
    public static int grammarTooBig = 0;
    public static int validTagged = 0;
    public static int validLR = 0;

    static Logger logger = Logger.getLogger(Converter.class.getName());

    public static void tagGrammar(File grammarFile)
            throws IOException, NoRulesException, ActionException {

        ANTLRInputStream input = new ANTLRInputStream(new FileInputStream(grammarFile));

        ANTLRv4Lexer lexer = new ANTLRv4Lexer(input);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        ANTLRv4Parser parser = new ANTLRv4Parser(tokens);

        ParseTree tree = parser.grammarSpec();

        MyVisitor visitor = new MyVisitor();

        visitor.visit(tree);

        var ruleMap = visitor.getRules();

        // map of symbol name to object
        HashMap<String, Node> nodeMap = new HashMap<>();
        for (var nt : visitor.getNonTerminals()) {
            nodeMap.put(nt, new NonTerminal(nt));
        }
        for (var t : visitor.getTerminals()) {
            nodeMap.put(t, new Terminal(t));
        }
        NonTerminal start = (NonTerminal) nodeMap.get(visitor.getStart());

        //
        ruleMap.forEach((k, alts) -> {
            var rule = (NonTerminal) nodeMap.get(k);
            for (var a : alts) {
                ArrayList<Pair<Node, EbnfSuffix>> l = new ArrayList<>();
                for (var elem : a) {
                    l.add(new Pair<>(nodeMap.get(elem.a), elem.b));
                }
                rule.addRule(l);
            }
        });

        if (start == null) {
            logger.log(Level.WARNING, "No rules found: " + grammarFile.getAbsolutePath() + "\n");
            throw new NoRulesException("No rules found: " + grammarFile.getAbsolutePath());
        }

        Grammar grammar = new Grammar(start);

        boolean hadLR = false;
        if (grammar.isLeftRecursive()) {
            lrCount += 1;
            grammar.removeLR();
            grammarsWithLR += grammarFile.getName() + "\n";
            hadLR = true;
        }

        grammar.tagByPrecedence(true);

        //remove non-terminals that are not matched in rules
        grammar.removeNonMatchingTagging();

        // repeat tagging excluding these terminals
        grammar.tagByPrecedence(true);

        grammar.convertToSimpleForm();

        // test if grammar is convertible to VPG

        boolean valid = false;

        if (grammar.getNotermCount() <= 70) {
            var graph = new DepGraph(grammar);
            graph.findCycles();
            valid = graph.isValid();
        } else {
            grammarTooBig++;
        }

        if (grammar.call.size() > 0) {
            taggedGrammars += 1;
            lrInVPGsCount += hadLR ? 1 : 0;
            if (valid) {
                validTagged += 1;
                if (hadLR) {
                    validLR ++;
                }
            }
        }
        if (valid) {
            validGrammars++;
        }


        // write results to the result folder
        String path = grammarFile.getParent().replace("grammars-v4", RES_PATH);
        writeResults(path, grammarFile.getName(), grammar, valid);

        if (DO_BRUTEFORCE) {
            var br = grammar.bruteForceTagging();
            var brCall = br.a;
            var brRet = br.b;

            if (!brCall.equals(grammar.call) || !brRet.equals(grammar.ret)) {
                diffWithBruteforce++;
                differentGrammars += grammarFile.getName() + "\n";
            }
            writeBruteForceThing(path, grammarFile.getName(), brCall, brRet,
                                 (!brCall.equals(grammar.call) || !brRet.equals(grammar.ret)));
        }
    }

    public static void writeBruteForceThing(String path, String fileName, Set<Terminal> call,
                                            Set<Terminal> ret, boolean isDifferent)
            throws IOException {
        fileName = fileName.replace(".g4", "") + "_tagged_all" + ".txt";
        File br = new File(path + File.separator + fileName);
        br.createNewFile();
        PrintWriter brWriter = new PrintWriter(br);
        brWriter.println("call\t" + call);
        brWriter.println("ret \t" + ret);
        brWriter.println(isDifferent ? "Different" : "Same");
        brWriter.close();
    }

    public static void writeResults(String path, String fileName, Grammar g, boolean isValid)
            throws IOException {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        fileName = fileName.replace(".g4", "") + "_tagged" + ".txt";
        File grammar = new File(path + File.separator + fileName);
        File info = new File(path + File.separator + fileName.replace(".g4", "") + "_info.txt");

        if (grammar.exists() || info.exists()) {
            throw new IOException(
                    "The grammar file already exists: " + grammar.getAbsolutePath() + "\n");
        }

        grammar.createNewFile();
        info.createNewFile();

        PrintWriter grammarWriter = new PrintWriter(grammar);
        grammarWriter.println("grammar " + fileName.replace(".g4", "") + ";");
        grammarWriter.print(g.printTaggedGrammar());
        grammarWriter.close();

        var pairSet = new HashSet<>(g.pairs);

        PrintWriter infoWriter = new PrintWriter(info);
        infoWriter.println("call\t" + g.call);
        infoWriter.println("ret \t" + g.ret);
        infoWriter.println("pairs: \t" + pairSet);
        infoWriter.println(isValid ? "Valid" : "Invalid");
        infoWriter.close();
    }

    public static void main(String[] args) throws IOException, NoRulesException {

        String grammars = "grammars-v4";
        File resFile = new File(RES_PATH);

        final int[] skipped = {0};
        final int[] fileCounter = {0};
        final int[] processedFiles = {0};

        FileUtils.deleteDirectory(resFile);
        if (!resFile.mkdir()) {
            logger.log(Level.SEVERE, "Could not create directory " + resFile.getAbsolutePath());
            return;
        }

        Files.walk(Paths.get(grammars)).forEach(path -> {

            // uncomment this to test on a specific grammar that looks nice
//                        if (!path.endsWith("testing.g4")) {
//                            return;
//                        }


            if (path.toFile().isFile() && path.toString().endsWith(".g4") &&
                    !path.toString().contains("antlr/antlr")) {
                fileCounter[0] += 1;
                if (path.toString().endsWith("Lexer.g4")) {
                    skipped[0] += 1;
                    logger.log(Level.INFO, "skipping lexer: " + path + "\n");
                } else {
                    logger.log(Level.INFO, "tagging " + path + "\n");
                    try {
                        tagGrammar(path.toFile());
                        processedFiles[0] += 1;
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "IOException: " + path + "\n");
                        skipped[0] += 1;
                    } catch (NoRulesException e) {
                        logger.log(Level.INFO, "NoRulesException: " + path + "\n");
                        skipped[0] += 1;
                    } catch (ActionException e) {
                        logger.log(Level.INFO, "Action in " + path + "\n");
                        skipped[0] += 1;
                    }
                }
            }
        });

        //        File generalInfo = new File(RES_PATH + File.separator + "general_info.txt");
        //        PrintWriter generalInfoWriter = new PrintWriter(generalInfo);
        //
        //
        //        generalInfoWriter.println("Total number of .g4 files: " + fileCounter[0]);
        //        generalInfoWriter.println("Skipped files: " + skipped[0]);
        //        generalInfoWriter.println("Processed files: " + processedFiles[0]);
        //        generalInfoWriter.println("tagged grammars:  " + taggedGrammars);
        //        generalInfoWriter.println("Left Recursive Grammars: " + lrCount);
        //        generalInfoWriter.println("Out of which " + lrInVPGsCount + " had left recursion");
        //        generalInfoWriter.close();

        System.out.println("Total number of .g4 files: " + fileCounter[0]);
        System.out.println("Skipped files: " + skipped[0]);
        System.out.println("Processed files: " + processedFiles[0]);
        System.out.println("Grammars with LR: " + lrCount);
        System.out.println("Tagged grammars: " + taggedGrammars);
        System.out.println("Tagged grammars with LR: " + lrInVPGsCount);
        System.out.println("Valid grammars: " + validGrammars);
        System.out.println("Valid tagged: " + validTagged);
        System.out.println("ValidLr " + validLR);
        System.out.println("skipped cause too big: " + grammarTooBig);
    }


}
