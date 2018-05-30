package org.intelligentjava.machinelearning.decisiontree;

import com.fasterxml.jackson.databind.JsonNode;
import org.intelligentjava.machinelearning.decisiontree.data.SimpleValue;
import org.intelligentjava.machinelearning.decisiontree.feature.P;
import org.intelligentjava.machinelearning.decisiontree.label.BooleanLabel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.intelligentjava.machinelearning.decisiontree.data.SimpleValue.classification;
import static org.intelligentjava.machinelearning.decisiontree.data.SimpleValue.data;
import static org.intelligentjava.machinelearning.decisiontree.feature.PredicateFeature.feature;
import static org.junit.jupiter.api.Assertions.*;

public class DecisionTreeTrainingTest {


    static final com.google.common.base.Function<JsonNode, Function<String, Object>> jsonValue = (j) -> j::get;
    

    /**
     * Test if decision tree correctly learns simple AND function.
     * Should learn tree like this:
     * x1 = true
     * /       \
     * yes       No
     * /           \
     * x2 = true      LABEL_FALSE
     * /    \
     * yes     No
     * /         \
     * LABEL_TRUE    LABEL_FALSE
     */
    @Test
    public void testTrainingAndFunction() {
        DecisionTree<String, Object> tree = new DecisionTree();
        String[] header = {"x1", "x2", "answer"};

        SimpleValue data1 = data(header, Boolean.TRUE, Boolean.TRUE, BooleanLabel.TRUE_LABEL);
        SimpleValue data2 = data(header, Boolean.TRUE, Boolean.FALSE, BooleanLabel.FALSE_LABEL);
        SimpleValue data3 = data(header, Boolean.FALSE, Boolean.TRUE, BooleanLabel.FALSE_LABEL);
        SimpleValue data4 = data(header, Boolean.FALSE, Boolean.FALSE, BooleanLabel.FALSE_LABEL);

        Predicate<Function<String, Object>> feature1 = feature("x1", Boolean.TRUE);
        Predicate<Function<String, Object>> feature2 = feature("x1", Boolean.FALSE);
        Predicate<Function<String, Object>> feature3 = feature("x2", Boolean.TRUE);
        Predicate<Function<String, Object>> feature4 = feature("x2", Boolean.FALSE);

        tree.put("answer", asList(data1, data2, data3, data4), asList(feature1, feature2, feature3, feature4));

        DecisionTree.Node<?> root = tree.root();

        assertEquals("x1 = true", root.toString()); 
        assertEquals(null, root.label); 

        assertEquals("x2 = true", root.get(0).toString());
        assertEquals(null, root.get(0).label); 
        assertTrue(root.get(0).get(0).isLeaf());
        assertEquals(BooleanLabel.TRUE_LABEL, root.get(0).get(0).label);
        assertTrue(root.get(0).get(1).isLeaf());
        assertEquals(BooleanLabel.FALSE_LABEL, root.get(0).get(1).label);

        assertTrue(root.get(1).isLeaf());
        assertEquals(BooleanLabel.FALSE_LABEL, root.get(1).label);

    }


    /**
     * Test if decision tree correctly learns simple OR function.
     * Should learn tree like this:
     * x1 = true
     * /       \
     * yes       No
     * /           \
     * LABEL_TRUE     x2 = true
     * /    \
     * yes     No
     * /         \
     * LABEL_TRUE    LABEL_FALSE
     */
    @Test
    public void testTrainingORFunction() {
        DecisionTree<String, Object> tree = new DecisionTree();
        String[] header = {"x1", "x2", "answer"};

        SimpleValue data1 = data(header, Boolean.TRUE, Boolean.TRUE, BooleanLabel.TRUE_LABEL);
        SimpleValue data2 = data(header, Boolean.TRUE, Boolean.FALSE, BooleanLabel.TRUE_LABEL);
        SimpleValue data3 = data(header, Boolean.FALSE, Boolean.TRUE, BooleanLabel.TRUE_LABEL);
        SimpleValue data4 = data(header, Boolean.FALSE, Boolean.FALSE, BooleanLabel.FALSE_LABEL);

        Predicate<Function<String, Object>> feature1 = feature("x1", Boolean.TRUE);
        Predicate<Function<String, Object>> feature2 = feature("x1", Boolean.FALSE);
        Predicate<Function<String, Object>> feature3 = feature("x2", Boolean.TRUE);
        Predicate<Function<String, Object>> feature4 = feature("x2", Boolean.FALSE);

        tree.put("answer", asList(data1, data2, data3, data4), asList(feature1, feature2, feature3, feature4));

        DecisionTree.Node<?> root = tree.root();
        assertEquals("x1 = true", root.toString()); 
        assertEquals(null, root.label); 

        assertTrue(root.get(0).isLeaf());
        assertEquals(BooleanLabel.TRUE_LABEL, root.get(0).label);

        assertEquals("x2 = true", root.get(1).toString());
        assertEquals(null, root.get(1).label);
        assertTrue(root.get(1).get(0).isLeaf());
        assertEquals(BooleanLabel.TRUE_LABEL, root.get(1).get(0).label);
        assertTrue(root.get(1).get(1).isLeaf());
        assertEquals(BooleanLabel.FALSE_LABEL, root.get(1).get(1).label);

    }


    /**
     * Test if decision tree correctly learns simple XOR function.
     * Should learn tree like this:
     * x1 = true
     * /       \
     * yes       No
     * /           \
     * x2 = true        x2 = true
     * /    \              /    \
     * yes     No          yes     No
     * /         \          /         \
     * LABEL_FALSE LABEL_TRUE  LABEL_TRUE LABEL_FALSE
     */
    @Test
    public void testTrainingXORFunction() {
        DecisionTree<String, Object> tree = new DecisionTree();
        String[] header = {"x1", "x2", "answer"};

        SimpleValue data1 = data(header, Boolean.TRUE, Boolean.TRUE, BooleanLabel.FALSE_LABEL);
        SimpleValue data2 = data(header, Boolean.TRUE, Boolean.FALSE, BooleanLabel.TRUE_LABEL);
        SimpleValue data3 = data(header, Boolean.FALSE, Boolean.TRUE, BooleanLabel.TRUE_LABEL);
        SimpleValue data4 = data(header, Boolean.FALSE, Boolean.FALSE, BooleanLabel.FALSE_LABEL);

        Predicate<Function<String, Object>> feature1 = feature("x1", Boolean.TRUE);
        Predicate<Function<String, Object>> feature2 = feature("x1", Boolean.FALSE);
        Predicate<Function<String, Object>> feature3 = feature("x2", Boolean.TRUE);
        Predicate<Function<String, Object>> feature4 = feature("x2", Boolean.FALSE);

        tree.put("answer", asList(data1, data2, data3, data4), asList(feature1, feature2, feature3, feature4));
        tree.print();

        DecisionTree.Node root = tree.root();
        assertEquals("x2 = true", root.toString()); 
        assertNull(root.label); 

        assertEquals("false", root.get(0).toString());
        assertEquals("true", root.get(1).toString());
    }

    @Test
    public void testLearnSimpleMoreLessFeature() {
        DecisionTree<String, Integer> tree = new DecisionTree();
        String[] header = {"x1", "answer"};

        tree.put(
                "answer",
                asList(
                        data(header, 1, BooleanLabel.FALSE_LABEL),
                        data(header, 2, BooleanLabel.FALSE_LABEL),
                        data(header, 3, BooleanLabel.TRUE_LABEL),
                        data(header, 4, BooleanLabel.TRUE_LABEL)),
                asList(
                        feature("x1", P.moreThan(0), "> 0"),
                        feature("x1", P.moreThan(1), "> 1"),
                        feature("x1", P.moreThan(2), "> 2"))
        );

        tree.print();

        DecisionTree.Node<?> root = tree.root();
        assertEquals("x1 > 2", root.toString()); 
        assertEquals(null, root.label); 

        assertTrue(root.get(0).isLeaf());
        assertEquals(BooleanLabel.TRUE_LABEL, root.get(0).label);
        assertTrue(root.get(1).isLeaf());
        assertEquals(BooleanLabel.FALSE_LABEL, root.get(1).label);


    }

    /**
     * Test classify function which finds path in decision tree to leaf node.
     *
     * @author Ignas
     */
    @Test
    public void testClassify() {

        
        DecisionTree tree = new DecisionTree();
        String[] header = {"x1", "x2", "answer"};

        SimpleValue data1 = data(header, Boolean.TRUE, Boolean.TRUE, BooleanLabel.TRUE_LABEL);
        SimpleValue data2 = data(header, Boolean.TRUE, Boolean.FALSE, BooleanLabel.FALSE_LABEL);
        SimpleValue data3 = data(header, Boolean.FALSE, Boolean.TRUE, BooleanLabel.FALSE_LABEL);
        SimpleValue data4 = data(header, Boolean.FALSE, Boolean.FALSE, BooleanLabel.FALSE_LABEL);

        Predicate<Function<String, Object>> feature1 = feature("x1", Boolean.TRUE);
        Predicate<Function<String, Object>> feature2 = feature("x1", Boolean.FALSE);
        Predicate<Function<String, Object>> feature3 = feature("x2", Boolean.TRUE);
        Predicate<Function<String, Object>> feature4 = feature("x2", Boolean.FALSE);

        tree.put("answer",
                asList(data1, data2, data3, data4),
                asList(feature1, feature2, feature3, feature4));

        
        String[] classificationHeader = {"x1", "x2"};
        assertEquals(BooleanLabel.TRUE_LABEL, tree.get(classification(classificationHeader, Boolean.TRUE, Boolean.TRUE)));
        assertEquals(BooleanLabel.FALSE_LABEL, tree.get(classification(classificationHeader, Boolean.TRUE, Boolean.FALSE)));
        assertEquals(BooleanLabel.FALSE_LABEL, tree.get(classification(classificationHeader, Boolean.FALSE, Boolean.TRUE)));
        assertEquals(BooleanLabel.FALSE_LABEL, tree.get(classification(classificationHeader, Boolean.FALSE, Boolean.FALSE)));
    }



























    @Test
    public void testRealDecisionTable() {


        RealDecisionTree tr = new RealDecisionTree(
            new FloatTable<>("a", "b", "x")
                .add(1, 1, 0)
                .add(1, 0, 1)
                .add(2, 1, 1)
                .add(5, 1, 0)
                .add(5, 0, 1),
                2,
                5,
                "LO", "HI");

        tr.print();

        List<DecisionTree.Node.LeafNode<Float>> leavesList = tr.leaves().collect(toList());
        assertEquals("[1.0, 1.0, 1.0, 1.0, 0.0]", leavesList.toString());

    }


}
