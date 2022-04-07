package org.folio.karate.results

class KarateModuleExecutionSummary {

    String name

    KarateExecutionResult executionResult = KarateExecutionResult.SUCCESS

    List<KarateFeatureExecutionSummary> features = []

    int featuresPassed = 0

    int featuresFailed = 0

    int featuresSkipped = 0

    KarateModuleExecutionSummary(String name) {
        this.name = name
    }

    void addFeaturesExecutionStatistics(int success, int failed, int skipped) {
        featuresPassed += success
        featuresFailed += failed
        featuresSkipped += skipped

        if (featuresFailed > 0) {
            executionResult = KarateExecutionResult.FAIL
        }
    }

    void addFeatureSummary(def summaryJson) {
        KarateFeatureExecutionSummary feature = new KarateFeatureExecutionSummary(
            name: summaryJson.name,
            description: summaryJson.description,
            packageQualifiedName: summaryJson.packageQualifiedName,
            passedCount: summaryJson.passedCount,
            failedCount: summaryJson.failedCount,
            scenarioCount: summaryJson.scenarioCount,
            failed: summaryJson.failed)

        features.add(feature)
    }

    int getFeaturesTotal() {
        featuresPassed + featuresFailed + featuresSkipped
    }


    @Override
    public String toString() {
        return "KarateModuleExecutionSummary{" +
            "name='" + name + '\'' +
            ", executionResult=" + executionResult +
            ", features=" + features +
            ", featuresPassed=" + featuresPassed +
            ", featuresFailed=" + featuresFailed +
            ", featuresSkipped=" + featuresSkipped +
            '}';
    }
}
