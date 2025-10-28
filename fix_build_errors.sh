#!/bin/bash
set -e

echo "Applying surgical fixes to AILive build errors..."

# Fix 1: MemoryAI.kt - Update MemoryStored message construction
echo "Fixing MemoryAI.kt lines 151-154..."

# The error is around line 151-154 where MemoryStored is created
# Old fields: embeddingId, content, timestamp, entry
# New fields: embeddingId, contentType, metadata

# Find and fix the MemoryStored construction
sed -i '/AIMessage.Cognition.MemoryStored(/,/)/c\
            AIMessage.Cognition.MemoryStored(\
                embeddingId = entry.id,\
                contentType = contentType,\
                metadata = mapOf(\
                    "importance" to importance.toString(),\
                    "timestamp" to entry.timestamp.toString()\
                )\
            )' app/src/main/java/com/ailive/memory/MemoryAI.kt

# Fix 2: MemoryAI.kt - Update MemoryRecalled if it exists
# New structure expects: query, results (List<MemoryEntry>), topKSimilarity
sed -i 's/results = entries.map.*$/results = entries,/g' app/src/main/java/com/ailive/memory/MemoryAI.kt

# Fix 3: MetaAI.kt line 311 - Change safetyRule to violationType  
echo "Fixing MetaAI.kt line 311..."
sed -i '311s/safetyRule =/violationType =/g' app/src/main/java/com/ailive/meta/MetaAI.kt

# Fix 4: MotorAI.kt line 245 - Change safetyRule to violationType
echo "Fixing MotorAI.kt line 245..."
sed -i '245s/safetyRule =/violationType =/g' app/src/main/java/com/ailive/motor/MotorAI.kt

# Fix 5: PredictiveAI.kt lines 68-71 - Update PredictionGenerated construction
echo "Fixing PredictiveAI.kt lines 68-71..."

# Old fields: action, expectedReward, cost
# New fields: scenarios (List<PredictedScenario>), recommendedAction

# Find the PredictionGenerated construction and replace it
sed -i '/AIMessage.Cognition.PredictionGenerated(/,/)/c\
            AIMessage.Cognition.PredictionGenerated(\
                scenarios = scenarios.map { outcome ->\
                    PredictedScenario(\
                        description = outcome.description,\
                        probability = outcome.probability,\
                        expectedValue = outcome.expectedValue\
                    )\
                },\
                recommendedAction = scenarios.maxByOrNull { it.expectedValue }?.description\
            )' app/src/main/java/com/ailive/predictive/PredictiveAI.kt

echo "All fixes applied!"
