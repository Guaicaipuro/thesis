package net.recommenders.rival.evaluation.metric;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runners.JUnit4;
import org.junit.runner.RunWith;

import net.recommenders.rival.core.DataModel;

import java.util.Map;
import net.recommenders.rival.evaluation.metric.ranking.Recall;

/**
 * @author <a href="http://github.com/alansaid">Alan</a>.
 */
@RunWith(JUnit4.class)
public class RecallTest {

    @Test
    public void testSameGroundtruthAsPredictions() {
        DataModel<Long, Long> predictions = new DataModel<Long, Long>();
        DataModel<Long, Long> test = new DataModel<Long, Long>();
        int nUsers = 20;
        int nItems = 15;
        for (long i = 1L; i < nUsers + 1; i++) {
            for (long j = 1L; j < nItems + 1; j++) {
                test.addPreference(i, j, i * j % 5 + 1.0);
                predictions.addPreference(i, j, i * j % 5 + 1.0);
            }
        }
        Recall recall = new Recall(predictions, test, 1.0, new int[]{5, 10, 20});

        recall.compute();

        assertEquals(1.0, recall.getValue(), 0.0);
        assertEquals(5.0 / nItems, recall.getValueAt(5), 0.0001);
        assertEquals(10.0 / nItems, recall.getValueAt(10), 0.0001);

        Map<Long, Double> recallPerUser = recall.getValuePerUser();
        for (Map.Entry<Long, Double> e : recallPerUser.entrySet()) {
            long user = e.getKey();
            double value = e.getValue();
            assertEquals(1.0, value, 0.0);
        }
    }

    @Test
    public void testOneUserTrecevalStrategySingleRelevance() {
        // groundtruth: ? 0 1 1
        // predictions: 3 4 5 1
        DataModel<Long, Long> test = new DataModel<Long, Long>();
        DataModel<Long, Long> predictions = new DataModel<Long, Long>();
        test.addPreference(1L, 2L, 0.0);
        test.addPreference(1L, 3L, 1.0);
        test.addPreference(1L, 4L, 1.0);
        predictions.addPreference(1L, 1L, 3.0);
        predictions.addPreference(1L, 2L, 4.0);
        predictions.addPreference(1L, 3L, 5.0);
        predictions.addPreference(1L, 4L, 1.0);

        Recall recall = new Recall(predictions, test, 1.0, new int[]{1, 2, 3, 4, 5});

        recall.compute();

        assertEquals(1.0, recall.getValue(), 0.001);
        assertEquals(0.5, recall.getValueAt(1), 0.001);
        assertEquals(0.5, recall.getValueAt(2), 0.001);
        assertEquals(0.5, recall.getValueAt(3), 0.001);
        assertEquals(1.0, recall.getValueAt(4), 0.001);
        assertEquals(1.0, recall.getValueAt(5), 0.001);

        Map<Long, Double> recallPerUser = recall.getValuePerUser();
        for (Map.Entry<Long, Double> e : recallPerUser.entrySet()) {
            long user = e.getKey();
            double value = e.getValue();
            assertEquals(1.0, value, 0.001);
        }
    }

    @Test
    public void testOneUserTrecevalStrategyMultipleRelevance() {
        // groundtruth: ? 0 1 2
        // predictions: 3 4 5 1
        DataModel<Long, Long> test = new DataModel<Long, Long>();
        DataModel<Long, Long> predictions = new DataModel<Long, Long>();
        test.addPreference(1L, 2L, 0.0);
        test.addPreference(1L, 3L, 1.0);
        test.addPreference(1L, 4L, 2.0);
        predictions.addPreference(1L, 1L, 3.0);
        predictions.addPreference(1L, 2L, 4.0);
        predictions.addPreference(1L, 3L, 5.0);
        predictions.addPreference(1L, 4L, 1.0);

        Recall recall = new Recall(predictions, test, 1.0, new int[]{1, 2, 3, 4, 5});

        recall.compute();

        assertEquals(1.0, recall.getValue(), 0.001);
        assertEquals(0.5, recall.getValueAt(1), 0.001);
        assertEquals(0.5, recall.getValueAt(2), 0.001);
        assertEquals(0.5, recall.getValueAt(3), 0.001);
        assertEquals(1.0, recall.getValueAt(4), 0.001);
        assertEquals(1.0, recall.getValueAt(5), 0.001);

        Map<Long, Double> recallPerUser = recall.getValuePerUser();
        for (Map.Entry<Long, Double> e : recallPerUser.entrySet()) {
            long user = e.getKey();
            double value = e.getValue();
            assertEquals(1.0, value, 0.001);
        }
    }
}
