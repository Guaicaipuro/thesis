package net.recommenders.rival.evaluation.metric;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import net.recommenders.rival.core.DataModel;
import net.recommenders.rival.core.SimpleParser;
import net.recommenders.rival.evaluation.metric.error.AbstractErrorMetric;
import net.recommenders.rival.evaluation.metric.ranking.NDCG;
import net.recommenders.rival.evaluation.parser.TrecEvalParser;
import net.recommenders.rival.evaluation.strategy.EvaluationStrategy;

/**
 * Runner for multiple evaluation metrics.
 *
 * @author <a href="http://github.com/abellogin">Alejandro</a>
 */
public class MultipleEvaluationMetricRunner {

    /**
     * Variables that represent the name of several properties in the file.
     */
    public static final String PREDICTION_FOLDER = "evaluation.pred.folder";
    public static final String PREDICTION_PREFIX = "evaluation.pred.prefix";
    public static final String PREDICTION_FILE_FORMAT = "evaluation.pred.format";
    public static final String TEST_FILE = "evaluation.test.file";
    public static final String OUTPUT_OVERWRITE = "evaluation.output.overwrite";
    public static final String OUTPUT_APPEND = "evaluation.output.append";
    public static final String OUTPUT_FOLDER = "evaluation.output.folder";
    public static final String METRICS = "evaluation.classes";
    public static final String RELEVANCE_THRESHOLD = "evaluation.relevance.threshold";
    public static final String RANKING_CUTOFFS = "evaluation.ranking.cutoffs";
    public static final String NDCG_TYPE = "evaluation.ndcg.type";
    public static final String ERROR_STRATEGY = "evaluation.error.strategy";
    public static final String METRIC_PER_USER = "evaluation.peruser";

    /**
     * Main method for running a single evaluation metric.
     *
     * @param args Arguments.
     * @throws Exception If file not found.
     */
    public static void main(String[] args) throws Exception {
        String propertyFile = System.getProperty("propertyFile");

        final Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propertyFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        run(properties);
    }

    /**
     * Run multiple evaluation metrics.
     *
     * @param properties The properties of the strategy.
     * @throws IOException if file not found.
     * @throws ClassNotFoundException when
     * @throws IllegalAccessException when
     * @throws IllegalArgumentException when
     * @throws InstantiationException when
     * @throws InvocationTargetException when
     * @throws NoSuchMethodException when
     * @throws SecurityException when
     */
    @SuppressWarnings("unchecked")
    public static void run(Properties properties) throws IOException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        EvaluationStrategy.OUTPUT_FORMAT recFormat = properties.getProperty(PREDICTION_FILE_FORMAT).equals(EvaluationStrategy.OUTPUT_FORMAT.TRECEVAL.toString()) ? EvaluationStrategy.OUTPUT_FORMAT.TRECEVAL : EvaluationStrategy.OUTPUT_FORMAT.SIMPLE;

        System.out.println("Parsing started: test file");
        File testFile = new File(properties.getProperty(TEST_FILE));
        DataModel<Long, Long> testModel = new SimpleParser().parseData(testFile);
        System.out.println("Parsing finished: test file");

        File predictionsFolder = new File(properties.getProperty(PREDICTION_FOLDER));
        String predictionsPrefix = properties.getProperty(PREDICTION_PREFIX);
        Set<String> predictionFiles = new HashSet<String>();
        getAllPredictionFiles(predictionFiles, predictionsFolder, predictionsPrefix);

        // read other parameters
        Boolean overwrite = Boolean.parseBoolean(properties.getProperty(OUTPUT_OVERWRITE, "false"));
        Boolean doAppend = Boolean.parseBoolean(properties.getProperty(OUTPUT_APPEND, "true"));
        Boolean perUser = Boolean.parseBoolean(properties.getProperty(METRIC_PER_USER, "false"));
        Double threshold = Double.parseDouble(properties.getProperty(RELEVANCE_THRESHOLD));
        int[] rankingCutoffs;// = null;
        // process info for each result file
        File resultsFolder = new File(properties.getProperty(OUTPUT_FOLDER));
        for (String file : predictionFiles) {
            File predictionFile = new File(predictionsPrefix + file);
            System.out.println("Parsing started: recommendation file");
            DataModel<Long, Long> predictions;// = null;
            switch (recFormat) {
                case SIMPLE:
                    predictions = new SimpleParser().parseData(predictionFile);
                    break;
                case TRECEVAL:
                    predictions = new TrecEvalParser().parseData(predictionFile);
                    break;
                default:
                    throw new AssertionError();
            }
            System.out.println("Parsing finished: recommendation file");
            File resultsFile = new File(resultsFolder, "eval" + "__" + predictionFile.getName());

            // get metrics
            String[] metricClassNames = properties.getProperty(METRICS).split(",");
            for (String metricClassName : metricClassNames) {
                // get metric
                Class<?> metricClass = Class.forName(metricClassName);
                EvaluationMetric<Long> metric;// = null;
                if (metricClassName.contains(".ranking.")) {
                    String[] cutoffs = properties.getProperty(RANKING_CUTOFFS).split(",");
                    rankingCutoffs = new int[cutoffs.length];
                    for (int i = 0; i < rankingCutoffs.length; i++) {
                        rankingCutoffs[i] = Integer.parseInt(cutoffs[i]);
                    }
                    if (metricClassName.endsWith("NDCG")) {
                        String ndcgType = properties.getProperty(NDCG_TYPE, "exp");
                        NDCG.TYPE nt = ndcgType.equalsIgnoreCase(NDCG.TYPE.EXP.toString()) ? NDCG.TYPE.EXP : NDCG.TYPE.LIN;
                        metric = (EvaluationMetric<Long>) metricClass.getConstructor(DataModel.class, DataModel.class, double.class, int[].class, NDCG.TYPE.class).newInstance(predictions, testModel, threshold.doubleValue(), rankingCutoffs, nt);
                    } else {
                        metric = (EvaluationMetric<Long>) metricClass.getConstructor(DataModel.class, DataModel.class, double.class, int[].class).newInstance(predictions, testModel, threshold.doubleValue(), rankingCutoffs);
                    }
                } else {
                    rankingCutoffs = new int[0];
                    String strategy = properties.getProperty(ERROR_STRATEGY);
                    AbstractErrorMetric.ErrorStrategy es = null;
                    for (AbstractErrorMetric.ErrorStrategy s : AbstractErrorMetric.ErrorStrategy.values()) {
                        if (strategy.equalsIgnoreCase(s.toString())) {
                            es = s;
                            break;
                        }
                    }
                    if (es == null) {
                        System.out.println("Invalid error strategy: " + strategy);
                        return;
                    }
                    metric = (EvaluationMetric<Long>) metricClass.getConstructor(DataModel.class, DataModel.class, AbstractErrorMetric.ErrorStrategy.class).newInstance(predictions, testModel, es);
                }
                // generate output
                EvaluationMetricRunner.generateOutput(testModel, rankingCutoffs, metric, metricClass.getSimpleName(), perUser, resultsFile, overwrite, doAppend);
            }
        }
    }

    /**
     * Get all prediction files.
     *
     * @param predictionFiles The splits.
     * @param path The path where the splits are.
     * @param predictionPrefix The prefix of the prediction files.
     */
    public static void getAllPredictionFiles(Set<String> predictionFiles, File path, String predictionPrefix) {
        for (File file : path.listFiles()) {
            if (file.isDirectory()) {
                getAllPredictionFiles(predictionFiles, file, predictionPrefix);
            } else if (file.getName().startsWith(predictionPrefix)) {
                predictionFiles.add(file.getAbsolutePath().replaceAll(predictionPrefix, ""));
            }
        }
    }
}
