///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author Juan Miguel Ogarrio
 * @author ps7z
 * @author jdramsey
 */
public final class GFciNg implements GraphSearch {

    private final DataSet dataSet;
    // The PAG being constructed.
    private Graph graph;

    // The background knowledge.
    private IKnowledge knowledge = new Knowledge2();

    // The conditional independence test.
    private IndependenceTest independenceTest;

    // Flag for complete rule set, true if should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = false;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The maxDegree for the fast adjacency search.
    private int maxDegree = -1;

    // The logger to use.
    private TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose = false;

    // The covariance matrix beign searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;

    // The sample size.
    int sampleSize;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // True iff one-edge faithfulness is assumed. Speed up the algorith for very large searches. By default false.
    private boolean faithfulnessAssumed = true;

    // The score.
    private Score score;

    private long elapsedTime;

    //============================CONSTRUCTORS============================//
    public GFciNg(DataSet dataSet, IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.dataSet = dataSet;
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        long time1 = System.currentTimeMillis();

        List<Node> nodes = getIndependenceTest().getVariables();

        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        Fas fas = new Fas(independenceTest);
        this.graph = fas.search();

        List<DataSet> dataSet1 = new ArrayList<>();
        dataSet1.add(this.dataSet);

        Lofs2 lofs1 = new Lofs2(graph, dataSet1);
        lofs1.setRule(Lofs2.Rule.R3);
        Graph r3Graph = lofs1.orient();
//
        IKnowledge required = createRequiredKnowledge(r3Graph);

        Fges fges = new Fges(score);
        fges.setInitialGraph(new EdgeListGraph(r3Graph));
        fges.setKnowledge(required);
        fges.setVerbose(verbose);
        fges.setNumPatternsToStore(0);
        fges.setFaithfulnessAssumed(faithfulnessAssumed);
        fges.setMaxDegree(maxDegree);
        fges.setOut(out);
        Graph fgesGraph = fges.search();

        graph.reorientAllWith(Endpoint.CIRCLE);
//        fciOrientbk(knowledge, graph, graph.getNodes());

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c)) continue;

                if (r3Graph.isDefCollider(a, b, c)) {
                    if (b.getName() .equalsIgnoreCase("X5")) {
                        System.out.println();
                    }

                    System.out.println("Copy collider from R3 graph not shielded in the FGES graph: " + a + "->" + b + "<-" + c);
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }

        SepsetProducer sepsets = new SepsetsGreedy(graph, independenceTest, null, maxDegree);

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
                    final List<Node> sepset = sepsets.getSepset(a, c);

                    if (sepset == null) {
                        continue;
                    }

                    if (!independenceTest.isIndependent(a, c, sepset)) {
                        continue;
                    }

                    System.out.println(SearchLogUtils.independenceFact(a, c, sepset));

                    if (sepset.contains(b)) {
                        continue;
                    }

                    sepset.add(b);

                    if (independenceTest.isDependent(a, c, sepset)) {
                        continue;
                    }

                    System.out.println(SearchLogUtils.dependenceFactMsg(a, c, sepset, independenceTest.getPValue()));

                    System.out.println("Estimate collider from triple shielded in the FGES graph: " + a + "->" + b + "<-" + c);
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setVerbose(verbose);
        fciOrient.setOut(out);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.doFinalOrientation(graph);

        GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        long time2 = System.currentTimeMillis();

        elapsedTime = time2 - time1;

        graph.setPag(true);

        return graph;
    }

    @Override
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * @param maxDegree The maximum indegree of the output graph.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Returns The maximum indegree of the output graph.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    // Due to Spirtes.
//    public void modifiedR0(Graph fgesGraph) {
//        graph.reorientAllWith(Endpoint.CIRCLE);
//        fciOrientbk(knowledge, graph, graph.getNodes());
//
//        List<Node> nodes = graph.getNodes();
//
//        for (Node b : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(b);
//
//            if (adjacentNodes.size() < 2) {
//                continue;
//            }
//
//            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
//            int[] combination;
//
//            while ((combination = cg.next()) != null) {
//                Node a = adjacentNodes.get(combination[0]);
//                Node c = adjacentNodes.get(combination[1]);
//
//                if (fgesGraph.isDefCollider(a, b, c)) {
//                    graph.setEndpoint(a, b, Endpoint.ARROW);
//                    graph.setEndpoint(c, b, Endpoint.ARROW);
//                } else if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
//                    List<Node> sepset = sepsets.getSepset(a, c);
//
//                    System.out.println("Sepset for " + a + " " + b + " is " + sepset);
//
//                    if (sepset != null && !sepset.contains(b)) {
//                        graph.setEndpoint(a, b, Endpoint.ARROW);
//                        graph.setEndpoint(c, b, Endpoint.ARROW);
//                    }
//                }
//            }
//        }
//    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only
     * R1-R4 (the rule set of the original FCI) should be used. False by
     * default.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set
     *                            should be used, false if only R1-R4 (the rule set of the original FCI)
     *                            should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of
     * unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1
     *                      if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public ICovarianceMatrix getCovMatrix() {
        return covarianceMatrix;
    }

    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    //===========================================PRIVATE METHODS=======================================//

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("info", "Finishing BK Orientation.");
    }

    private IKnowledge createRequiredKnowledge(Graph graph) {
        IKnowledge knwl = new Knowledge2(graph.getNodeNames());

        List<Node> nodes = graph.getNodes();

        int numOfNodes = nodes.size();
        for (int i = 0; i < numOfNodes; i++) {
            for (int j = i + 1; j < numOfNodes; j++) {
                Node n1 = nodes.get(i);
                Node n2 = nodes.get(j);

                if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                    continue;
                }

                Edge edge = graph.getEdge(n1, n2);
                if (edge == null) {
                    continue;
                } else if (edge.isDirected()) {
                    knwl.setRequired(edge.getNode1().getName(), edge.getNode2().getName());
                } else if (Edges.isUndirectedEdge(edge)) {
                    knwl.setRequired(n1.getName(), n2.getName());
                    knwl.setRequired(n2.getName(), n1.getName());
                }
            }
        }

        return knwl;
    }
}