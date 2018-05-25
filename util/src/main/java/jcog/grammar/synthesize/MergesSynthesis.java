// Copyright 2015-2016 Stanford University
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package jcog.grammar.synthesize;


import jcog.grammar.synthesize.util.GrammarUtils.*;
import jcog.grammar.synthesize.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

import static jcog.grammar.synthesize.util.GrammarUtils.*;

public class MergesSynthesis {
    public static NodeMerges getMergesMultiple(Iterable<Node> roots, Predicate<String> oracle) {
        NodeMerges merges = new NodeMerges();
        NodeMerges processed = new NodeMerges();
        for (Node first : roots) {
            for (Node second : roots) {
                if (processed.contains(first, second)) {
                    continue;
                }
                processed.add(first, second);
                merges.addAll(getMergesSingle(first, second, oracle));
            }
        }
        return merges;
    }

    public static NodeMerges getMergesSingle(Node firstRoot, Node secondRoot, Predicate<String> oracle) {
        NodeMerges merges = new NodeMerges();
        NodeMerges processedMerges = new NodeMerges();
        MultivalueMap<Node, String> pairFirst = getAllExamples(firstRoot);
        MultivalueMap<Node, String> pairSecond = getAllExamples(secondRoot);
        for (Node first : getAllNodes(firstRoot)) {
            for (Node second : getAllNodes(secondRoot)) {
                if (processedMerges.contains(first, second)) {
                    continue;
                }
                processedMerges.add(first, second);
                getMergesHelper(first, second, pairFirst, pairSecond, oracle, merges);
            }
        }
        return merges;
    }

    private static void getMergesHelper(Node first, Node second, MultivalueMap<Node, String> firstExampleMap, MultivalueMap<Node, String> secondExampleMap, Predicate<String> oracle, NodeMerges merges) {
        if (first.equals(second)) {
            return;
        }
        if (!(first instanceof RepetitionNode) || !(second instanceof RepetitionNode)) {
            return;
        }
        Node firstRep = ((RepetitionNode) first).rep;
        Node secondRep = ((RepetitionNode) second).rep;
        if (firstRep instanceof ConstantNode || firstRep instanceof MultiConstantNode || secondRep instanceof ConstantNode || secondRep instanceof MultiConstantNode) {
            return;
        }
        if (isMultiAlternationRepetitionConstant(firstRep, true) || isMultiAlternationRepetitionConstant(secondRep, true)) {
            return;
        }
        Collection<String> firstExamplesSimple = new ArrayList<>();
        Collection<String> secondExamplesSimple = new ArrayList<>();
        firstExamplesSimple.add(secondRep.getData().example + secondRep.getData().example);
        secondExamplesSimple.add(firstRep.getData().example + firstRep.getData().example);
        if (!GrammarSynthesis.getCheck(oracle, firstRep.getData().context, firstExamplesSimple) || !GrammarSynthesis.getCheck(oracle, secondRep.getData().context, secondExamplesSimple)) {
            return;
        }
        Collection<String> firstExamples = new ArrayList<>();
        for (String example : secondExampleMap.get(secondRep)) {
            firstExamples.add(example + example);
        }
        Collection<String> secondExamples = new ArrayList<>();
        for (String example : firstExampleMap.get(firstRep)) {
            secondExamples.add(example + example);
        }
        if ((isStructuredExample(firstRep) && isStructuredExample(secondRep))
                || (GrammarSynthesis.getCheck(oracle, firstRep.getData().context, firstExamples) && GrammarSynthesis.getCheck(oracle, secondRep.getData().context, secondExamples))) {
            Log.info("MERGE NODE FIRST:\n" + firstRep.getData().context.pre + " ## " + firstRep.getData().example + " ## " + firstRep.getData().context.post);
            Log.info("MERGE NODE SECOND:\n" + secondRep.getData().context.pre + " ## " + secondRep.getData().example + " ## " + secondRep.getData().context.post);
            merges.add(firstRep, secondRep);
        }
    }

    private static void getAllExamplesHelper(Node node, MultivalueMap<Node, String> examples) {
        for (Node child : node.getChildren()) {
            getAllExamplesHelper(child, examples);
        }
        if (node instanceof RepetitionNode) {
            RepetitionNode repNode = (RepetitionNode) node;
            for (String example : examples.get(repNode.start)) {
                examples.add(repNode, example + repNode.rep.getData().example + repNode.end.getData().example);
            }
            for (String example : examples.get(repNode.rep)) {
                examples.add(repNode, repNode.start.getData().example + example + repNode.end.getData().example);
            }
            for (String example : examples.get(repNode.end)) {
                examples.add(repNode, repNode.start.getData().example + repNode.rep.getData().example + example);
            }
        } else if (node instanceof MultiConstantNode) {
            MultiConstantNode mconstNode = (MultiConstantNode) node;
            String example = mconstNode.getData().example;
            for (int i = 0; i < mconstNode.characterChecks.size(); i++) {
                String pre = example.substring(0, i);
                String post = example.substring(i + 1);
                for (char c : mconstNode.characterChecks.get(i)) {
                    examples.add(mconstNode, pre + c + post);
                }
            }
        } else if (node instanceof AlternationNode) {
            AlternationNode altNode = (AlternationNode) node;
            for (String example : examples.get(altNode.first)) {
                examples.add(altNode, example);
            }
            for (String example : examples.get(altNode.second)) {
                examples.add(altNode, example);
            }
        } else if (node instanceof ConstantNode) {
            examples.add(node, node.getData().example);
        } else if (node instanceof MultiAlternationNode) {
            for (Node child : node.getChildren()) {
                for (String example : examples.get(child)) {
                    examples.add(node, example);
                }
            }
        } else {
            throw new RuntimeException("Invalid node type: " + node.getClass().getName());
        }
    }

    private static MultivalueMap<Node, String> getAllExamples(Node root) {
        MultivalueMap<Node, String> allExamples = new MultivalueMap<>();
        getAllExamplesHelper(root, allExamples);
        return allExamples;
    }


    private static boolean isMultiAlternationRepetitionConstant(Node node, boolean isParentRep) {
        return GrammarSynthesis.getMultiAlternationRepetitionConstantChildren(node, isParentRep).hasT();
    }

    private static boolean isStructuredExample(Node node) {
        for (Node descendant : getDescendants(node)) {
            if (!(descendant instanceof MultiConstantNode)) {
                continue;
            }
            MultiConstantNode mconstNode = (MultiConstantNode) descendant;
            for (Set<Character> checks : mconstNode.characterChecks) {
                if (checks.size() == 1) {
                    return true;
                }
            }
        }
        return false;
    }
}