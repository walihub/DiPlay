#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TASK_JAVA=/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home/bin
cd "$TASK_ROOT"
mkdir -p build/arcfox-media-integration-tests
"$TASK_JAVA/javac" -d build/arcfox-media-integration-tests $(rg --files tools/arcfox-media/tests/stubs -g '*.java') tools/arcfox-media/java/local/arcfox/media/*.java tools/arcfox-media/tests/*.java
"$TASK_JAVA/java" -cp build/arcfox-media-integration-tests SourcePolicyTest
"$TASK_JAVA/java" -cp build/arcfox-media-integration-tests MediaBridgeIntegrationTest

"$TASK_JAVA/java" -cp build/arcfox-media-integration-tests NativeLyricsTest
