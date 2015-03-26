package net.recommenders.rival.evaluation.strategy;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.recommenders.rival.core.DataModel;
import net.recommenders.rival.core.SimpleParser;
import net.recommenders.rival.evaluation.strategy.EvaluationStrategy.Pair;

/**
 * Runner for a single strategy.
 *
 * @author <a href="http://github.com/abellogin">Alejandro</a>
 */
public class StrategyRunner {

    /**
     * Variables that represent the name of several properties in the file.
     */
    public static final String TRAINING_FILE = "split.training.file";
    public static final String TEST_FILE = "split.test.file";
    public static final String INPUT_FILE = "recommendation.file";
    public static final String OUTPUT_FORMAT = "output.format";
    public static final String OUTPUT_OVERWRITE = "output.overwrite";
    public static final String OUTPUT_FILE = "output.file.ranking";
    public static final String GROUNDTRUTH_FILE = "output.file.groundtruth";
    public static final String STRATEGY = "strategy.class";
    public static final String RELEVANCE_THRESHOLD = "strategy.relevance.threshold";
    public static final String RELPLUSN_N = "strategy.relplusn.N";
    public static final String RELPLUSN_SEED = "strategy.relplusn.seed";

    /**
     * Main method for running a single evaluation strategy.
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
     * Run a single evaluation strategy.
     *
     * @param properties The properties of the strategy.
     * @throws IOException when a file cannot be parsed
     * @throws ClassNotFoundException when the name of the class does not exist
     * @throws IllegalAccessException when the strategy cannot be instantiated
     * @throws IllegalArgumentException when some property cannot be parsed
     * @throws InstantiationException when the strategy cannot be instantiated
     * @throws InvocationTargetException when the strategy cannot be
     * instantiated
     * @throws NoSuchMethodException when the strategy cannot be instantiated
     * @throws SecurityException when the strategy cannot be instantiated
     */
    public static void run(Properties properties) throws IOException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        // read splits
        System.out.println("Parsing started: training file");
        File trainingFile = new File(properties.getProperty(TRAINING_FILE));
        DataModel<Long, Long> trainingModel = new SimpleParser().parseData(trainingFile);
        System.out.println("Parsing finished: training file");
        System.out.println("Parsing started: test file");
        File testFile = new File(properties.getProperty(TEST_FILE));
        DataModel<Long, Long> testModel = new SimpleParser().parseData(testFile);
        System.out.println("Parsing finished: test file");
        // read other parameters
        File inputFile = new File(properties.getProperty(INPUT_FILE));
        Boolean overwrite = Boolean.parseBoolean(properties.getProperty(OUTPUT_OVERWRITE, "false"));
        File rankingFile = new File(properties.getProperty(OUTPUT_FILE));
        File groundtruthFile = new File(properties.getProperty(GROUNDTRUTH_FILE));
        EvaluationStrategy.OUTPUT_FORMAT format = properties.getProperty(OUTPUT_FORMAT).equals(EvaluationStrategy.OUTPUT_FORMAT.TRECEVAL.toString()) ? EvaluationStrategy.OUTPUT_FORMAT.TRECEVAL : EvaluationStrategy.OUTPUT_FORMAT.SIMPLE;
        Double threshold = Double.parseDouble(properties.getProperty(RELEVANCE_THRESHOLD));
        String strategyClassName = properties.getProperty(STRATEGY);
        Class<?> strategyClass = Class.forName(strategyClassName);
        // get strategy
        EvaluationStrategy<Long, Long> strategy = null;
        if (strategyClassName.contains("RelPlusN")) {
            Integer N = Integer.parseInt(properties.getProperty(RELPLUSN_N));
            Long seed = Long.parseLong(properties.getProperty(RELPLUSN_SEED));
            strategy = new RelPlusN(trainingModel, testModel, N, threshold, seed);
        } else {
            Object strategyObj = strategyClass.getConstructor(DataModel.class, DataModel.class, double.class).newInstance(trainingModel, testModel, threshold);
            if (strategyObj instanceof EvaluationStrategy) {
                @SuppressWarnings("unchecked")
                EvaluationStrategy<Long, Long> strategyTemp = (EvaluationStrategy<Long, Long>) strategyObj;
                strategy = strategyTemp;
            }
        }
        // read recommendations: user \t item \t score
        final Map<Long, List<Pair<Long, Double>>> mapUserRecommendations = new HashMap<Long, List<Pair<Long, Double>>>();
        BufferedReader in = new BufferedReader(new FileReader(inputFile));
        String line = null;
        while ((line = in.readLine()) != null) {
            readLine(line, mapUserRecommendations);
        }
        in.close();
        // generate output
        generateOutput(testModel, mapUserRecommendations, strategy, format, rankingFile, groundtruthFile, overwrite);
    }

    /**
     * Generates the output of the evaluation.
     *
     * @param testModel The test model.
     * @param mapUserRecommendations The recommendations for the users.
     * @param strategy The strategy.
     * @param format The printer format.
     * @param rankingFile The ranking file.
     * @param groundtruthFile The ground truth.
     * @param overwrite Whether or not to overwrite results file.
     * @throws FileNotFoundException If file not found.
     */
    public static void generateOutput(final DataModel<Long, Long> testModel, final Map<Long, List<EvaluationStrategy.Pair<Long, Double>>> mapUserRecommendations, EvaluationStrategy<Long, Long> strategy, EvaluationStrategy.OUTPUT_FORMAT format, File rankingFile, File groundtruthFile, Boolean overwrite) throws FileNotFoundException {
        PrintStream outRanking = null;
        if (rankingFile.exists() && !overwrite) {
            System.out.println("Ignoring " + rankingFile);
        } else {
            outRanking = new PrintStream(rankingFile);
        }
        PrintStream outGroundtruth = null;
        if (groundtruthFile.exists() && !overwrite) {
            System.out.println("Ignoring " + groundtruthFile);
        } else {
            outGroundtruth = new PrintStream(groundtruthFile);
        }
        for (Long user : testModel.getUsers()) {
            if (outRanking != null) {
                final List<EvaluationStrategy.Pair<Long, Double>> allScoredItems = mapUserRecommendations.get(user);
                if (allScoredItems == null) {
                    continue;
                }
                final Set<Long> items = strategy.getCandidateItemsToRank(user);
                final List<EvaluationStrategy.Pair<Long, Double>> scoredItems = new ArrayList<EvaluationStrategy.Pair<Long, Double>>();
                for (EvaluationStrategy.Pair<Long, Double> scoredItem : allScoredItems) {
                    if (items.contains(scoredItem.getFirst())) {
                        scoredItems.add(scoredItem);
                    }
                }
                strategy.printRanking(user, scoredItems, outRanking, format);
            }
            if (outGroundtruth != null) {
                strategy.printGroundtruth(user, outGroundtruth, format);
            }
        }
        if (outRanking != null) {
            outRanking.close();
        }
        if (outGroundtruth != null) {
            outGroundtruth.close();
        }
    }

    /**
     * Read a file from the recommended items file.
     *
     * @param line The line.
     * @param mapUserRecommendations The recommendations for the users.
     */
    public static void readLine(String line, Map<Long, List<Pair<Long, Double>>> mapUserRecommendations) {
        String[] toks = line.split("\t");
        // mymedialite format: user \t [item:score,item:score,...]
        if (line.contains(":") && line.contains(",")) {
            Long user = Long.parseLong(toks[0]);
            String items = toks[1].replace("[", "").replace("]", "");
            for (String pair : items.split(",")) {
                String[] pairToks = pair.split(":");
                Long item = Long.parseLong(pairToks[0]);
                Double score = Double.parseDouble(pairToks[1]);
                List<Pair<Long, Double>> userRec = mapUserRecommendations.get(user);
                if (userRec == null) {
                    userRec = new ArrayList<Pair<Long, Double>>();
                    mapUserRecommendations.put(user, userRec);
                }
                userRec.add(new Pair<Long, Double>(item, score));
            }
        } else {
            Long user = Long.parseLong(toks[0]);
            Long item = Long.parseLong(toks[1]);
            Double score = Double.parseDouble(toks[2]);
            List<Pair<Long, Double>> userRec = mapUserRecommendations.get(user);
            if (userRec == null) {
                userRec = new ArrayList<Pair<Long, Double>>();
                mapUserRecommendations.put(user, userRec);
            }
            userRec.add(new Pair<Long, Double>(item, score));
        }
    }
}
